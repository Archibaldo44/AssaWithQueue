package vib.assaqueue.communication

import android.util.Log

class IndustrialDoorsProtocolHandler : CTIProtocolHandler() {

    private var sendQueue: TSI2Queue<IndustrialDoorMessage>? = null

    override suspend fun doHandshake(): Boolean {
        sendQueue = TSI2Queue(this::sendToDevice)

        putInQueue(IndustrialDoorMessage.alive)
        val responseMessage = putInQueueWithResponse(IndustrialDoorMessage.readSwVersion)
        Log.i("vitDebug", "===")
        Log.i("vitDebug", "doHandshake: Door replied with $responseMessage")
        Log.i("vitDebug", "doHandshake: Door replied with ${responseMessage?.toReadablePayload()}")
        Log.i("vitDebug", "===")
        putInQueue(IndustrialDoorMessage.ack)
        return responseMessage != null
    }

    override suspend fun readSwVersion(): String? {
        putInQueue(IndustrialDoorMessage.alive)
        val responseMessage = putInQueueWithResponse(IndustrialDoorMessage.readSwVersion)
        putInQueue(IndustrialDoorMessage.ack)
        return responseMessage?.toReadablePayload()
    }

    override suspend fun readParameter(parameterId: Int): String? {
        putInQueue(IndustrialDoorMessage.alive)
        val message = IndustrialDoorMessage.createReadParameterMessage(parameterId)
        val responseMessage = putInQueueWithResponse(message)
        putInQueue(IndustrialDoorMessage.ack)
        return responseMessage?.toReadablePayload()
    }

    override suspend fun writeParameter(parameterId: Int, parameterValue: Int): String? {
        putInQueue(IndustrialDoorMessage.alive)
        val message = IndustrialDoorMessage.createWriteParameterMessage(parameterId, parameterValue)
        val responseMessage = putInQueueWithResponse(message)
//        putInQueue(IndustrialDoorMessage.ack)
        return responseMessage?.toReadablePayload()
    }

    override fun getMessage(byteList: List<Byte>) = IndustrialDoorMessage.fromByteList(byteList)

    override fun handleFrame(message: Message) {
        message as IndustrialDoorMessage
        sendQueue?.checkReply(message)
    }

    override fun beforeDisconnect() {
        cleanUp()
    }

    override fun cleanUp() {
        sendQueue?.cancel()
        sendQueue = null
    }

    private fun putInQueue(message: IndustrialDoorMessage) = sendQueue?.putInQueue(message) ?: Unit

    private suspend fun putInQueueWithResponse(message: IndustrialDoorMessage): IndustrialDoorMessage? =
        sendQueue?.putInQueueWithResponse(message)

    private fun sendToDevice(message: Message) {
        device?.write(message.toByteArray())
    }
}
