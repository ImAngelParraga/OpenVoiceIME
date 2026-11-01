package dev.rankis.openime.audio

import java.io.File

interface VoiceRecorder {
    val maxAmplitude: Int
    fun start(): File
    fun stop(): File?
    fun cancel()
}
