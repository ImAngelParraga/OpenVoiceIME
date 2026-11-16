package dev.rankis.openime.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors

internal fun deleteAbandonedRecordings(cacheDir: File) {
    cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("openime-") && it.extension == "m4a" }
        ?.forEach { it.delete() }
}

class AudioRecorder(private val context: Context) : VoiceRecorder {
    private data class Capture(val recorder: MediaRecorder, val file: File)

    private class PendingStart {
        @Volatile var cancelled = false
        var capture: Capture? = null
    }

    private var capture: Capture? = null
    private var pendingStart: PendingStart? = null

    override val maxAmplitude: Int
        get() = runCatching { capture?.recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    override fun start(onResult: (Result<File>) -> Unit) {
        check(capture == null && pendingStart == null) { "Recorder already running or starting" }
        val start = PendingStart()
        pendingStart = start
        recorderWorker.execute {
            if (start.cancelled) return@execute
            val result = runCatching { startCapture(start) }
            start.capture = result.getOrNull()
            mainHandler.post {
                if (pendingStart !== start) return@post
                pendingStart = null
                capture = result.getOrNull()
                onResult(result.map { it.file })
            }
        }
    }

    private fun startCapture(start: PendingStart): Capture {
        val file = File.createTempFile("openime-", ".m4a", context.cacheDir)
        var mediaRecorder: MediaRecorder? = null
        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(16000)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            check(!start.cancelled)
            recorder.start()
            return Capture(recorder, file)
        } catch (failure: Throwable) {
            mediaRecorder?.let(::releaseRecorder)
            runCatching { file.delete() }
            throw failure
        }
    }

    override fun pause() {
        checkNotNull(capture) { "Recorder is not running" }.recorder.pause()
    }

    override fun resume() {
        checkNotNull(capture) { "Recorder is not running" }.recorder.resume()
    }

    override fun stop(): File? {
        val current = capture ?: return null
        capture = null
        val stopped = runCatching { current.recorder.stop() }.isSuccess
        releaseRecorder(current.recorder)
        if (!stopped) {
            current.file.delete()
            return null
        }
        return current.file
    }

    override fun cancel() = discard(releaseInBackground = false)

    override fun cancelForKeyboardReturn() = discard(releaseInBackground = true)

    private fun discard(releaseInBackground: Boolean) {
        pendingStart?.let { start ->
            pendingStart = null
            start.cancelled = true
            // Release before a newer start can open the mic, even across service instances.
            recorderWorker.execute { start.capture?.let(::discardCapture) }
        }
        val current = capture ?: return
        capture = null
        if (releaseInBackground) {
            recorderWorker.execute { discardCapture(current) }
        } else {
            discardCapture(current)
        }
    }

    private fun discardCapture(capture: Capture) {
        runCatching { capture.recorder.stop() }
        releaseRecorder(capture.recorder)
        runCatching { capture.file.delete() }
    }

    private fun releaseRecorder(mediaRecorder: MediaRecorder) {
        runCatching { mediaRecorder.reset() }
        runCatching { mediaRecorder.release() }
    }

    private companion object {
        val recorderWorker = Executors.newSingleThreadExecutor()
        val mainHandler = Handler(Looper.getMainLooper())
    }
}
