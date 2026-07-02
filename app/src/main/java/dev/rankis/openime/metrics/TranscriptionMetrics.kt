package dev.rankis.openime.metrics

import java.util.Locale

data class TranscriptionMetricsSnapshot(
    val totalCount: Long,
    val successCount: Long,
    val failureCount: Long,
    val totalBytes: Long,
    val totalDurationMillis: Long,
    val buckets: List<TranscriptionMetricsBucket>,
) {
    val averageDurationMillis: Long
        get() = average(totalDurationMillis, totalCount)

    val averageBytes: Long
        get() = average(totalBytes, totalCount)
}

data class TranscriptionMetricsBucket(
    val key: String,
    val label: String,
    val count: Long,
    val successCount: Long,
    val totalBytes: Long,
    val totalDurationMillis: Long,
) {
    val averageDurationMillis: Long
        get() = average(totalDurationMillis, count)

    val averageBytes: Long
        get() = average(totalBytes, count)
}

data class SizeBucket(
    val key: String,
    val label: String,
    val maxBytesInclusive: Long?,
)

val TRANSCRIPTION_SIZE_BUCKETS = listOf(
    SizeBucket("lt_64kb", "<64 KB", 64L * 1024L),
    SizeBucket("kb_64_256", "64-256 KB", 256L * 1024L),
    SizeBucket("kb_256_1024", "256 KB-1 MB", 1024L * 1024L),
    SizeBucket("gt_1mb", ">1 MB", null),
)

fun sizeBucketFor(bytes: Long): SizeBucket {
    val normalized = bytes.coerceAtLeast(0L)
    return TRANSCRIPTION_SIZE_BUCKETS.first { bucket ->
        bucket.maxBytesInclusive == null || normalized <= bucket.maxBytesInclusive
    }
}

fun formatTranscriptionMetrics(snapshot: TranscriptionMetricsSnapshot): String {
    if (snapshot.totalCount == 0L) {
        return "No voice uploads recorded yet."
    }

    val lines = mutableListOf(
        "Uploads: ${snapshot.totalCount} (${snapshot.successCount} OK, ${snapshot.failureCount} failed)",
        "Average: ${formatMillis(snapshot.averageDurationMillis)} for ${formatBytes(snapshot.averageBytes)}",
    )

    snapshot.buckets.filter { it.count > 0L }.forEach { bucket ->
        lines += "${bucket.label}: ${formatMillis(bucket.averageDurationMillis)} avg, " +
            "${bucket.count} upload(s), ${bucket.successCount} OK, ${formatBytes(bucket.averageBytes)} avg"
    }
    return lines.joinToString("\n")
}

fun formatMillis(millis: Long): String {
    return if (millis < 1_000L) {
        "${millis} ms"
    } else {
        String.format(Locale.US, "%.1f s", millis / 1_000.0)
    }
}

fun formatBytes(bytes: Long): String {
    return if (bytes < 1024L * 1024L) {
        "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    } else {
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun average(total: Long, count: Long): Long {
    return if (count <= 0L) 0L else total / count
}
