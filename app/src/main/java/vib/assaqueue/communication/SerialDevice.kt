package vib.assaqueue.communication

abstract class SerialDevice(var name: String, val address: String) {

    private var connectionListener: ((connected: ConnectionStatus) -> Unit)? = null
    protected var byteReceivedListener: (List<Byte>) -> Unit = {}

    var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
        private set(value) {
            field = value
            connectionListener?.invoke(value)
        }

    open suspend fun connectAndListen(
        byteReceivedListener: (bytes: List<Byte>) -> Unit,
        connectionListener: (ConnectionStatus) -> Unit
    ) {
        this.byteReceivedListener = byteReceivedListener
        this.connectionListener = connectionListener
    }

    protected open fun updateConnectionStatus(
        newConnectionStatus: ConnectionStatus
    ) {
        this.connectionStatus = newConnectionStatus
    }

    abstract fun write(byteArray: ByteArray)

    abstract fun disconnect(failure: Boolean)
}
