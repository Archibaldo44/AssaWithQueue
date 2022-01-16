package vib.assaqueue.communication

import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class OutData<T : Message>(
    val message: T,
    val beforeQueueContinuation: CancellableContinuation<T?>? = null,
)

class TSI2Queue<T : Message>(val sendFunction: (Message) -> Unit) {

    private var messageQueue = Channel<OutData<T>>(Channel.UNLIMITED)
    private var afterQueueContinuation: CancellableContinuation<T>? = null
    private var sentMessage: OutData<T>? = null

    private val job: Job = CoroutineScope(Dispatchers.Default).launch {
        while (isActive) {
            messageQueue.receive().onReceive()
        }
    }

    private suspend fun OutData<T>.onReceive() {
        if (beforeQueueContinuation == null) {
            sendFunction(message)
        } else {
            sentMessage = this
            val incomingMessage = withTimeoutOrNull(500) {
                suspendCancellableCoroutine<T?> { cancellableContinuation ->
                    afterQueueContinuation = cancellableContinuation
                    sendFunction(message)
                }
            }
            beforeQueueContinuation.resume(incomingMessage, null)
        }
    }

    fun putInQueue(message: T) {
        OutData(message).let {
            messageQueue.trySendBlocking(it).onFailure {throwable ->
                Log.e("vitDebug", "TSI2Queue.putInQueue: threw EXCEPTION ${throwable?.message}")
            }
        }
    }

    suspend fun putInQueueWithResponse(message: T): T? {
        return suspendCancellableCoroutine {
            runBlocking {
                val outData = OutData(message, it)
                messageQueue.send(outData)
            }
        }
    }

    fun checkReply(incomingMessage: T): Boolean {
        if (sentMessage?.message?.isReply?.invoke(incomingMessage) == true) {
            afterQueueContinuation?.resume(incomingMessage, null)
            return true
        }
        return false
    }

    fun cancel() {
        afterQueueContinuation?.cancel()
        job.cancel()
        messageQueue.close()
    }
}
