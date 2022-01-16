package vib.assaqueue.communication

abstract class ProtocolHandler {

    var device: SerialDevice? = null

    abstract suspend fun doHandshake(): Boolean

    abstract suspend fun readSwVersion(): String?

    abstract suspend fun readParameter(parameterId: Int): String?

    abstract suspend fun writeParameter(parameterId: Int, parameterValue: Int): String?

    abstract fun onReceiveBytes(bytes: List<Byte>)

    abstract fun beforeDisconnect()

    open fun cleanUp() {}
}