package vib.assaqueue.communication

import android.app.Service
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

open class Repository constructor(context: Context) {

    protected var communicationService: CommunicationService? = null
        private set
    protected val protocolHandler: ProtocolHandler? get() = communicationService?.protocolHandler
    private val serviceConnection = object : ServiceConnection {
        // Called when the connection with the service is established
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            communicationService =
                (service as CommunicationService.SerialCommunicationBinder).getService()
            communicationService?.connectionStatus?.observeForever {
                it?.let { connectionStatus ->
                    onConnectionStatusChanged(connectionStatus)
                }
            }
        }

        // Called when the connection with the service disconnects unexpectedly
        override fun onServiceDisconnected(className: ComponentName) {
            communicationService = null
        }
    }

    init {
        context.bindService(
            Intent(context, CommunicationService::class.java),
            serviceConnection,
            Service.BIND_AUTO_CREATE
        )
    }

    open fun onConnectionStatusChanged(connectionStatus: ConnectionStatus) {}
}