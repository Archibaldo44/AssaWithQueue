package vib.assaqueue.communication

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CommunicationService : Service() {

    private val _connectionStatus = MutableLiveData<ConnectionStatus>()
    val connectionStatus: LiveData<ConnectionStatus>
        get() = _connectionStatus

    var currentDevice: SerialDevice? = null
        private set

    var protocolHandler: ProtocolHandler? = null
        protected set

    init {
        _connectionStatus.postValue(ConnectionStatus.UNPLUGGED)
    }

    private fun setConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.postValue(status)
    }

    fun disconnect(
        failure: Boolean = false,
    ) {
        protocolHandler?.beforeDisconnect()
        currentDevice?.disconnect(failure) ?: kotlin.run {
            _connectionStatus.postValue(if (failure) ConnectionStatus.FAILED else ConnectionStatus.DISCONNECTED)
        }
        currentDevice = null
        protocolHandler = null
    }

    fun connect(device: SerialDevice, handler: ProtocolHandler) {
        protocolHandler?.beforeDisconnect()
        currentDevice?.disconnect(false)
        currentDevice = device
        GlobalScope.launch {
            device.connectAndListen(
                byteReceivedListener = { bytes -> handler.onReceiveBytes(bytes) },
                connectionListener = connectionListener(device, handler)
            )
        }
    }

    private fun connectionListener(
        device: SerialDevice,
        handler: ProtocolHandler,
    ): (ConnectionStatus) -> Unit {
        return { connectionStatus ->
            if (currentDevice == device && connectionStatus != ConnectionStatus.CONNECTED)
                setConnectionStatus(connectionStatus)

            if (connectionStatus == ConnectionStatus.CONNECTED) {
                GlobalScope.launch {
                    handler.device = device
                    val handshakeResult = handler.doHandshake()
                    if (!handshakeResult) {
                        device.disconnect(true)
                    } else {
                        protocolHandler = handler
                        setConnectionStatus(ConnectionStatus.CONNECTED)
                    }
                }
            } else if (connectionStatus == ConnectionStatus.FAILED && currentDevice == device) {
                protocolHandler?.cleanUp()
                currentDevice = null
            }
        }
    }

    suspend fun readSwVersion(): String? {
        return if (_connectionStatus.value == ConnectionStatus.CONNECTED) protocolHandler?.readSwVersion() else null
    }

    suspend fun readParameter(parameterId: Int): String? {
        return if (_connectionStatus.value == ConnectionStatus.CONNECTED) protocolHandler?.readParameter(
            parameterId
        ) else null
    }

    suspend fun writeParameter(parameterId: Int, parameterValue: Int): String? {
        return if (_connectionStatus.value == ConnectionStatus.CONNECTED) protocolHandler?.writeParameter(
            parameterId,
            parameterValue
        ) else null
    }

    open inner class SerialCommunicationBinder : Binder() {
        open fun getService(): CommunicationService = this@CommunicationService
    }

    override fun onBind(intent: Intent): IBinder {
        return SerialCommunicationBinder()
    }

    override fun onDestroy() {
        protocolHandler?.beforeDisconnect()
        super.onDestroy()
    }
}