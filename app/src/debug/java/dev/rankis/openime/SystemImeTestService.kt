package dev.rankis.openime

import dev.rankis.openime.audio.RecordingFocus
import dev.rankis.openime.audio.VoiceRecorder
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.stt.SttProvider
import dev.rankis.openime.stt.TranscriptionResult
import java.io.File

/** Isolated recorder for tests that exercise Android's actual IME binding. */
class SystemImeTestService : RemoteSttInputMethodService() {
    val testRecorder = TestRecorder()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun createSttProvider() = object : SttProvider {
        override suspend fun transcribe(
            audioFile: File,
            settings: AppSettings,
            timeoutMillis: Long,
        ) = TranscriptionResult.Success("shortcut dictation")
    }

    override fun createRecorder(): VoiceRecorder = testRecorder
    override fun createRecordingFocus() = object : RecordingFocus {
        override fun request() = Unit
        override fun abandon() = Unit
    }

    inner class TestRecorder : VoiceRecorder {
        var starts = 0
            private set
        var active = false
            private set
        var file: File? = null
            private set
        override val maxAmplitude = 12_000
        override fun start(): File {
            check(!active)
            starts += 1
            active = true
            return File.createTempFile("system-ime-test-", ".m4a", cacheDir).apply {
                writeBytes(byteArrayOf(1, 2, 3))
                file = this
            }
        }
        override fun pause() = Unit
        override fun resume() = Unit
        override fun stop(): File? { active = false; return file }
        override fun cancel() {
            if (active) file?.delete()
            active = false
        }
    }

    companion object {
        var instance: SystemImeTestService? = null
            private set
    }
}
