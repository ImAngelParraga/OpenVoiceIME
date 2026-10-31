package dev.rankis.openime.stt

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal data class HttpBody(val code: Int, val text: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

internal suspend fun Call.awaitBody(): HttpBody = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val result = runCatching {
                // Keep cancellation attached until the complete body has been consumed.
                response.use { HttpBody(it.code, it.body?.string().orEmpty()) }
            }
            continuation.resumeWith(result)
        }
    })
}
