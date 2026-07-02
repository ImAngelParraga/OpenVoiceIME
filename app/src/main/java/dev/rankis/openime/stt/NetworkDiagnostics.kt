package dev.rankis.openime.stt

import dev.rankis.openime.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class NetworkDiagnostics(
    private val client: OkHttpClient = diagnosticClient(),
) {
    suspend fun test(settings: AppSettings): DiagnosticResult = withContext(Dispatchers.IO) {
        val request = buildOpenAiDiagnosticRequest(settings)

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    DiagnosticResult.Success("Transcription endpoint reachable")
                } else {
                    DiagnosticResult.Failure(mapHttpResponse(response.code, response.body?.string()))
                }
            }
        } catch (e: Exception) {
            DiagnosticResult.Failure(e.toTranscriptionError())
        }
    }
}

fun buildOpenAiDiagnosticRequest(settings: AppSettings): Request {
    val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("model", settings.normalizedModel())
        .addFormDataPart("temperature", "0.0")
        .addFormDataPart(
            "file",
            "openime-probe.wav",
            silentWavProbe().toRequestBody("audio/wav".toMediaType()),
        )
        .build()

    return Request.Builder()
        .url("${settings.normalizedBaseUrl()}/v1/audio/transcriptions")
        .header("Authorization", "Bearer ${settings.apiToken}")
        .post(body)
        .build()
}

sealed class DiagnosticResult {
    data class Success(val message: String) : DiagnosticResult()
    data class Failure(val error: TranscriptionError) : DiagnosticResult()
}

fun Throwable.toTranscriptionError(): TranscriptionError = when (this) {
    is UnknownHostException -> TranscriptionError.UnknownHost(message.orEmpty())
    is SocketTimeoutException -> TranscriptionError.Timeout
    is ConnectException -> TranscriptionError.ConnectionRefused(message.orEmpty())
    else -> TranscriptionError.Network(message ?: javaClass.simpleName)
}

private fun diagnosticClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

private fun silentWavProbe(): ByteArray {
    val sampleRate = 16_000
    val channelCount = 1
    val bitsPerSample = 16
    val dataSize = sampleRate / 10 * channelCount * bitsPerSample / 8
    val byteRate = sampleRate * channelCount * bitsPerSample / 8
    val blockAlign = channelCount * bitsPerSample / 8

    return ByteArray(44 + dataSize).apply {
        writeAscii(0, "RIFF")
        writeIntLe(4, 36 + dataSize)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeIntLe(16, 16)
        writeShortLe(20, 1)
        writeShortLe(22, channelCount)
        writeIntLe(24, sampleRate)
        writeIntLe(28, byteRate)
        writeShortLe(32, blockAlign)
        writeShortLe(34, bitsPerSample)
        writeAscii(36, "data")
        writeIntLe(40, dataSize)
    }
}

private fun ByteArray.writeAscii(offset: Int, value: String) {
    value.forEachIndexed { index, char -> this[offset + index] = char.code.toByte() }
}

private fun ByteArray.writeIntLe(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
    this[offset + 2] = (value shr 16).toByte()
    this[offset + 3] = (value shr 24).toByte()
}

private fun ByteArray.writeShortLe(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value shr 8).toByte()
}
