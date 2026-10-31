package dev.rankis.openime.stt

fun transcriptionTimeoutMillis(audioDurationMillis: Long, audioBytes: Long): Long {
    // Recordings use 64 kbit/s AAC; size is a fallback when duration is unavailable.
    val duration = if (audioDurationMillis > 0L) audioDurationMillis else audioBytes.coerceAtLeast(0L) / 8L
    return (30_000L + 2L * duration.coerceIn(0L, 450_000L)).coerceIn(45_000L, 900_000L)
}
