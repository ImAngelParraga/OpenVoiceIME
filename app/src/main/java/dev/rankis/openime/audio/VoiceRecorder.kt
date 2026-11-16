package dev.rankis.openime.audio

import java.io.File

interface VoiceRecorder {
    val maxAmplitude: Int
    // Delivers an accepted start or failure on the caller’s main thread.
    fun start(onResult: (Result<File>) -> Unit)
    fun pause()
    fun resume()
    fun stop(): File?
    fun cancel()
    fun cancelForKeyboardReturn() = cancel()
}
