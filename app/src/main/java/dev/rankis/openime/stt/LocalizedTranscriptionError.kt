package dev.rankis.openime.stt

import android.content.Context
import dev.rankis.openime.R

fun TranscriptionError.localizedMessage(context: Context): String {
    return when (val error = this) {
        TranscriptionError.Timeout -> context.getString(R.string.transcription_timeout_retry)
        TranscriptionError.Unauthorized -> context.getString(R.string.transcription_unauthorized)
        TranscriptionError.ServerBusy -> context.getString(R.string.transcription_server_busy)
        TranscriptionError.EmptyTranscript -> context.getString(R.string.transcription_empty)
        is TranscriptionError.UnknownHost -> context.getString(R.string.transcription_unknown_host)
        is TranscriptionError.ConnectionRefused -> context.getString(R.string.transcription_connection_refused)
        is TranscriptionError.Http -> error.detail?.let {
            context.getString(R.string.transcription_http_detail_format, error.code, it)
        } ?: context.getString(R.string.transcription_http_format, error.code)
        is TranscriptionError.Network -> context.getString(R.string.transcription_network_format, error.detail)
        is TranscriptionError.Parse -> context.getString(R.string.transcription_parse)
    }
}
