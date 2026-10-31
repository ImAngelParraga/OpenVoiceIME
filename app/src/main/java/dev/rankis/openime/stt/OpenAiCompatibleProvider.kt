package dev.rankis.openime.stt

import dev.rankis.openime.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    override suspend fun transcribe(audioFile: File, settings: AppSettings, timeoutMillis: Long): TranscriptionResult = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() <= 0L) {
            return@withContext TranscriptionResult.Failure(TranscriptionError.Network("recording file is empty"))
        }

        val mediaType = "audio/mp4".toMediaType()
        val request = buildOpenAiTranscriptionRequest(audioFile, mediaType.toString(), settings)

        try {
            withTimeout(timeoutMillis) {
                val call = client.newCall(request)
                call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
                val response = call.awaitBody()
                if (response.isSuccessful) {
                    parseTranscriptionResponse(response.text)
                } else {
                    TranscriptionResult.Failure(mapHttpResponse(response.code, response.text))
                }
            }
        } catch (_: TimeoutCancellationException) {
            TranscriptionResult.Failure(TranscriptionError.Timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TranscriptionResult.Failure(e.toTranscriptionError())
        }
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
    settings.prompt?.let { bodyBuilder.addFormDataPart("prompt", it) }
    bodyBuilder.addFormDataPart("temperature", "0.0")

    return Request.Builder()
        .url("${settings.normalizedBaseUrl()}/v1/audio/transcriptions")
        .header("Authorization", "Bearer ${settings.apiToken}")
        .post(bodyBuilder.build())
        .build()
}

fun parseTranscriptText(json: String): String? {
    return runCatching {
        JSONObject(json).opt("text") as? String
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
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
}
