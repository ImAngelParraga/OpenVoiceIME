package dev.rankis.openime.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

class RecordingAudioFocus(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setOnAudioFocusChangeListener { }
        .build()

    private var focusGranted = false

    fun request() {
        if (focusGranted) {
            return
        }
        val result = audioManager.requestAudioFocus(focusRequest)
        focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focusGranted) {
            Log.i(TAG, "Audio focus request denied; continuing recording")
        }
    }

    fun abandon() {
        if (!focusGranted) {
            return
        }
        audioManager.abandonAudioFocusRequest(focusRequest)
        focusGranted = false
    }

    private companion object {
        const val TAG = "OpenVoiceIME"
    }
}
