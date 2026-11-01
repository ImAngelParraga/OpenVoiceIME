package dev.rankis.openime.stt

fun transcriptionTimeoutMillis(audioDurationMillis: Long, audioBytes: Long): Long {
    // Recordings use 64 kbit/s AAC; size is a fallback when duration is unavailable.
    val duration = if (audioDurationMillis > 0L) audioDurationMillis else audioBytes.coerceAtLeast(0L) / 8L
    return (5_000L + duration.coerceIn(0L, 900_000L)).coerceIn(10_000L, 900_000L)
}
