package vib.assaqueue.communication

import vib.assaqueue.R

enum class ConnectionStatus {
    CONNECTED, CONNECTING, DISCONNECTED, UNPLUGGED, FAILED;

    fun getResourceString(useFailureReason: Boolean = true) =
        when (this) {
            DISCONNECTED -> R.string.disconnected
            CONNECTING -> R.string.connecting
            CONNECTED -> R.string.connected
            UNPLUGGED -> R.string.connected
            FAILED -> R.string.failed
        }
}