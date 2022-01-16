package vib.assaqueue.communication

import android.util.Log

abstract class CTIProtocolHandler : ProtocolHandler() {

    abstract fun getMessage(byteList: List<Byte>): Message?

    abstract fun handleFrame(message: Message)

    override fun onReceiveBytes(bytes: List<Byte>) {
        if (bytes.distinct().size <= 1) {  // if only zeros - drop this "message"
            return
        }
        Log.i("vitDebugCommunication", " in <<< ${bytes.map { it.toUByte().toInt() }}")
        val message = getMessage(bytes)
        if (message == null) {
            Log.e("vitDebug", "CTIProtocolHandler.onReceiveBytes(): CANNOT convert received bytes into a Message.")
        } else {
            handleFrame(message)
        }
    }
}