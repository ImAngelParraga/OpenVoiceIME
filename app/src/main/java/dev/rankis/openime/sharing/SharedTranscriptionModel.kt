package dev.rankis.openime.sharing

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import dev.rankis.openime.R
import dev.rankis.openime.settings.SettingsStore
import dev.rankis.openime.settings.validateSettings
import dev.rankis.openime.settings.withAppLocale
import dev.rankis.openime.stt.OpenAiCompatibleProvider
import dev.rankis.openime.stt.TranscriptionResult
import dev.rankis.openime.stt.localizedMessage
import dev.rankis.openime.stt.transcriptionTimeoutMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SharedTranscriptState {
    data class Working(val importing: Boolean) : SharedTranscriptState()
    data class Ready(val text: String) : SharedTranscriptState()
    data class Failed(val message: String, val canRetry: Boolean) : SharedTranscriptState()
}

class SharedTranscriptionModel(application: Application) : AndroidViewModel(application) {
    private val context = application.withAppLocale()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<SharedTranscriptState>(SharedTranscriptState.Working(true))
    val state = mutableState.asStateFlow()
    private var audio: SharedAudio? = null
    private var source: Uri? = null
    private var started = false
    private var work: Job? = null

    fun start(intent: Intent) {
        if (started) return
        started = true
        @Suppress("DEPRECATION")
        val uri = runCatching { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) }.getOrNull()
        if (intent.action != Intent.ACTION_SEND || uri?.scheme != "content" ||
            (intent.clipData?.itemCount ?: 0) > 1
        ) {
            fail(R.string.share_invalid, false)
            return
        }
        source = uri
        transcribe()
    }

    fun retry() {
        if (mutableState.value !is SharedTranscriptState.Failed) return
        transcribe()
    }

    private fun transcribe() {
        if (work?.isActive == true || source == null) return
        work = scope.launch {
            try {
                val settings = SettingsStore(context).load()
                if (!validateSettings(settings).isValid) {
                    fail(R.string.share_configure, true)
                    return@launch
                }
                mutableState.value = SharedTranscriptState.Working(audio == null)
                if (audio == null) audio = SharedAudioImporter(context).import(requireNotNull(source))
                val input = requireNotNull(audio)
                mutableState.value = SharedTranscriptState.Working(false)
                when (val result = OpenAiCompatibleProvider().transcribe(
                    input.file, settings, transcriptionTimeoutMillis(input.durationMillis, input.file.length()),
                )) {
                    is TranscriptionResult.Success -> mutableState.value = SharedTranscriptState.Ready(result.text)
                    is TranscriptionResult.Failure -> mutableState.value = SharedTranscriptState.Failed(
                        result.error.localizedMessage(context), true,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SharedAudioException) {
                fail(when (error.reason) {
                    SharedAudioException.Reason.TOO_LARGE -> R.string.share_too_large
                    SharedAudioException.Reason.INVALID -> R.string.share_invalid
                    SharedAudioException.Reason.UNREADABLE -> R.string.share_unreadable
                }, false)
            } catch (_: Exception) {
                fail(R.string.share_unreadable, audio != null)
            }
        }
    }

    fun cancel() {
        work?.cancel()
        fail(R.string.share_cancelled, true)
    }

    private fun fail(message: Int, canRetry: Boolean) {
        mutableState.value = SharedTranscriptState.Failed(context.getString(message), canRetry)
    }

    override fun onCleared() {
        scope.cancel()
        audio?.file?.delete()
    }
}
