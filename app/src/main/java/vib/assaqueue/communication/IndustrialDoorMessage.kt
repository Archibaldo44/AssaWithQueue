package vib.assaqueue.communication

import android.util.Log

class IndustrialDoorMessage(
    val header: List<Byte>,
    val payload: List<Byte> = emptyList(),
    private val isReplyLambda: ((IndustrialDoorMessage) -> Boolean)? = { true }
) : Message(
    { message ->
        (message as? IndustrialDoorMessage)?.let { isReplyLambda?.invoke(it) } == true
    }
) {

    override fun toByteArray(): ByteArray = header.toByteArray() + payload + byteArrayOf(0, 0)

    fun toReadablePayload(): String = String(payload.toByteArray())

    companion object {

        val alive = IndustrialDoorMessage(listOf(2, 2, 0, 0, 0, 0, 0, 0, 0, 0))
        val ack = IndustrialDoorMessage(listOf(2, 1, 0, 0, 0, 0, 0, 0, 0, 0))
        val readSwVersion = IndustrialDoorMessage(
            header = listOf(2, 9, 0, 4, 0, 0, 0, 0, 0, 2),
            payload = listOf(3, 15)
        )

        fun fromByteList(bytes: List<Byte>): IndustrialDoorMessage? {
            if (bytes.size < 12) {
                Log.e("vitDebug", "Received message has wrong format: $bytes")
                return null
            }
            val payloadLength = bytes[9].toUByte().toInt()
            if (bytes.size < 10 + payloadLength + 2) {
                Log.e("vitDebug", "Received message has wrong format: $bytes")
                return null
            }
            return IndustrialDoorMessage(
                header = bytes.subList(0, 10),
                payload = bytes.subList(10, 10 + payloadLength)
            )
        }

        fun createReadParameterMessage(parameterId: Int): IndustrialDoorMessage {
            return IndustrialDoorMessage(
                header = listOf(2, 9, 0, 4, 0, 0, 0, 0, 0, 2),
                payload = listOf(3, parameterId.toByte())
            )
        }

        fun createWriteParameterMessage(parameterId: Int, parameterValue: Int): IndustrialDoorMessage {
            return IndustrialDoorMessage(
                header = listOf(2, 10, 0, 4, 0, 0, 0, 0, 0, 6),
                payload = mutableListOf(3, parameterId).plus(intToByteArray(parameterValue, 4).asList().reversed()) as List<Byte>
//                payload = mutableListOf(3, 22, 0 , 50, 56, 52)
            )
        }

        private fun intToByteArray(data: Int, size: Int = 4): ByteArray =
            ByteArray(size) { i -> (data shr (i * 8)).toByte() }
    }
}
