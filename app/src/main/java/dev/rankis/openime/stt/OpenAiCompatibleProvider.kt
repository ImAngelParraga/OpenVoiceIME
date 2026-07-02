package dev.rankis.openime.stt

import android.os.SystemClock
import android.util.Log
import dev.rankis.openime.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider(
    private val client: OkHttpClient = defaultClient(),
) : SttProvider {
    private val warmupLock = Any()
    private var warmupCall: okhttp3.Call? = null

    private val warmupClient = client.newBuilder()
        .callTimeout(2, TimeUnit.SECONDS)
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    override suspend fun transcribe(audioFile: File, settings: AppSettings): TranscriptionResult = withContext(Dispatchers.IO) {
        cancelWarmup()
        if (!audioFile.exists() || audioFile.length() <= 0L) {
            return@withContext TranscriptionResult.Failure(TranscriptionError.Network("recording file is empty"))
        }

        val mediaType = "audio/mp4".toMediaType()
        val request = buildOpenAiTranscriptionRequest(audioFile, mediaType.toString(), settings)

        try {
            val startedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "Uploading ${audioFile.length()} bytes to ${settings.normalizedBaseUrl()} language=${settings.languageCode ?: "auto"}")
            client.newCall(request).execute().use { response ->
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                Log.i(TAG, "STT response HTTP ${response.code} after ${elapsedMs}ms")
                if (!response.isSuccessful) {
                    return@withContext TranscriptionResult.Failure(
                        mapHttpResponse(response.code, response.body?.string()),
                    )
                }

                val responseText = response.body?.string().orEmpty()
                logServerTimings(responseText)
                parseTranscriptionResponse(responseText)
            }
        } catch (e: Exception) {
            val error = e.toTranscriptionError()
            Log.w(TAG, "STT upload failed: ${error.userMessage}", e)
            TranscriptionResult.Failure(error)
        }
    }

    override suspend fun warmUp(settings: AppSettings) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${settings.normalizedBaseUrl()}/health")
            .get()
            .build()
        runCatching {
            val startedAt = SystemClock.elapsedRealtime()
            val call = warmupClient.newCall(request)
            synchronized(warmupLock) {
                warmupCall = call
            }
            call.execute().use { response ->
                Log.i(TAG, "Server warmup HTTP ${response.code} after ${SystemClock.elapsedRealtime() - startedAt}ms")
            }
        }.onFailure {
            Log.i(TAG, "Server warmup skipped: ${it.javaClass.simpleName}")
        }.also {
            synchronized(warmupLock) {
                warmupCall = null
            }
        }
        Unit
    }

    private fun cancelWarmup() {
        synchronized(warmupLock) {
            warmupCall?.cancel()
            warmupCall = null
        }
    }

    private companion object {
        const val TAG = "OpenVoiceIME"
    }
}

fun buildOpenAiTranscriptionRequest(
    audioFile: File,
    mediaType: String,
    settings: AppSettings,
): Request {
    val bodyBuilder = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("model", settings.normalizedModel())
        .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType.toMediaType()))

    settings.languageCode?.let { bodyBuilder.addFormDataPart("language", it) }
    bodyBuilder.addFormDataPart("temperature", "0.0")

    return Request.Builder()
        .url("${settings.normalizedBaseUrl()}/v1/audio/transcriptions")
        .header("Authorization", "Bearer ${settings.apiToken}")
        .post(bodyBuilder.build())
        .build()
}

private fun logServerTimings(json: String) {
    runCatching {
        val timings = JSONObject(json).optJSONObject("timings") ?: return
        Log.i("OpenVoiceIME", "Server timings: $timings")
    }
}

fun parseTranscriptText(json: String): String? {
    return runCatching {
        JSONObject(json).takeIf { it.has("text") }?.getString("text")
    }.getOrNull()
}

fun parseTranscriptionResponse(json: String): TranscriptionResult {
    val transcript = parseTranscriptText(json)
        ?: return TranscriptionResult.Failure(TranscriptionError.Parse("missing text field"))
    if (transcript.isBlank()) {
        return TranscriptionResult.Failure(TranscriptionError.EmptyTranscript)
    }
    return TranscriptionResult.Success(transcript)
}

private fun defaultClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()
}
