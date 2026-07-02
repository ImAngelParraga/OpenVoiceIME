package dev.rankis.openime.stt

import dev.rankis.openime.settings.AppSettings
import java.io.File

interface SttProvider {
    suspend fun transcribe(audioFile: File, settings: AppSettings): TranscriptionResult

    suspend fun warmUp(settings: AppSettings) = Unit
}

sealed class TranscriptionResult {
    data class Success(val text: String) : TranscriptionResult()
    data class Failure(val error: TranscriptionError) : TranscriptionResult()
}

sealed class TranscriptionError(val userMessage: String) {
    data object Timeout : TranscriptionError("Server did not respond")
    data object Unauthorized : TranscriptionError("Token is invalid")
    data object ServerBusy : TranscriptionError("Server GPU is busy")
    data object EmptyTranscript : TranscriptionError("No speech detected")
    data class UnknownHost(val detail: String) : TranscriptionError("Cannot resolve server hostname")
    data class ConnectionRefused(val detail: String) : TranscriptionError("Cannot connect to server")
    data class Http(val code: Int, val detail: String? = null) : TranscriptionError(
        if (detail.isNullOrBlank()) {
            "Server returned HTTP $code"
        } else {
            "Server returned HTTP $code: $detail"
        },
    )
    data class Network(val detail: String) : TranscriptionError("Network error: $detail")
    data class Parse(val detail: String) : TranscriptionError("Could not parse server response")
}

fun mapHttpCode(code: Int): TranscriptionError = when (code) {
    401, 403 -> TranscriptionError.Unauthorized
    503 -> TranscriptionError.ServerBusy
    else -> TranscriptionError.Http(code)
}

fun mapHttpResponse(code: Int, body: String?): TranscriptionError = when (code) {
    401, 403 -> TranscriptionError.Unauthorized
    503 -> TranscriptionError.ServerBusy
    else -> TranscriptionError.Http(code, body.toHttpDetail())
}

private fun String?.toHttpDetail(): String? {
    val compact = this
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return compact
}
