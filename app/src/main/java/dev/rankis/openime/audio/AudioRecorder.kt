package dev.rankis.openime.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    val maxAmplitude: Int
        get() = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun start(): File {
        check(recorder == null) { "Recorder already running" }
        val file = File.createTempFile("openime-", ".m4a", context.cacheDir)
        var mediaRecorder: MediaRecorder? = null

        return try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            val configuredRecorder = mediaRecorder ?: error("Recorder construction failed")
            configuredRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            configuredRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            configuredRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            configuredRecorder.setAudioChannels(1)
            configuredRecorder.setAudioSamplingRate(16000)
            configuredRecorder.setAudioEncodingBitRate(64000)
            configuredRecorder.setOutputFile(file.absolutePath)
            configuredRecorder.prepare()
            configuredRecorder.start()

            recorder = configuredRecorder
            currentFile = file
            file
        } catch (failure: Throwable) {
            mediaRecorder?.let(::releaseRecorder)
            runCatching { file.delete() }
            throw failure
        }
    }

    fun stop(): File? {
        val file = currentFile
        val mediaRecorder = recorder
        recorder = null
        currentFile = null
        if (mediaRecorder == null) {
            return file
        }
        runCatching { mediaRecorder.stop() }
        releaseRecorder(mediaRecorder)
        return file
    }

    fun cancel() {
        val file = currentFile
        val mediaRecorder = recorder
        recorder = null
        currentFile = null
        if (mediaRecorder != null) {
            runCatching { mediaRecorder.stop() }
            releaseRecorder(mediaRecorder)
        }
        file?.let { runCatching { it.delete() } }
    }

    private fun releaseRecorder(mediaRecorder: MediaRecorder) {
        runCatching { mediaRecorder.reset() }
        runCatching { mediaRecorder.release() }
    }
}
