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
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioChannels(1)
        mediaRecorder.setAudioSamplingRate(16000)
        mediaRecorder.setAudioEncodingBitRate(64000)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()

        recorder = mediaRecorder
        currentFile = file
        return file
    }

    fun stop(): File? {
        val file = currentFile
        val mediaRecorder = recorder ?: return file
        runCatching { mediaRecorder.stop() }
        mediaRecorder.reset()
        mediaRecorder.release()
        recorder = null
        currentFile = null
        return file
    }

    fun cancel() {
        val file = currentFile
        val mediaRecorder = recorder
        if (mediaRecorder != null) {
            runCatching { mediaRecorder.stop() }
            mediaRecorder.reset()
            mediaRecorder.release()
        }
        recorder = null
        currentFile = null
        file?.delete()
    }
}
