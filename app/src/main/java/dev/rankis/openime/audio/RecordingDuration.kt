package dev.rankis.openime.audio

class RecordingDuration(private val now: () -> Long) {
    private var accumulated = 0L
    private var startedAt: Long? = null

    val elapsedMillis: Long
        get() = accumulated + (startedAt?.let { (now() - it).coerceAtLeast(0L) } ?: 0L)

    fun start() {
        accumulated = 0L
        startedAt = now()
    }

    fun pause() {
        accumulated = elapsedMillis
        startedAt = null
    }

    fun resume() {
        check(startedAt == null) { "Recording already running" }
        startedAt = now()
    }
}
