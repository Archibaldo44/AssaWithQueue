package vib.assaqueue.communication

abstract class Message(val isReply: ((Message) -> Boolean)? = null) {

    abstract fun toByteArray(): ByteArray
}
