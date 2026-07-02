package dev.rankis.openime.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionMetricsTest {
    @Test
    fun choosesSizeBuckets() {
        assertEquals("lt_64kb", sizeBucketFor(10L * 1024L).key)
        assertEquals("kb_64_256", sizeBucketFor(100L * 1024L).key)
        assertEquals("kb_256_1024", sizeBucketFor(700L * 1024L).key)
        assertEquals("gt_1mb", sizeBucketFor(2L * 1024L * 1024L).key)
    }

    @Test
    fun formatsEmptySnapshot() {
        val text = formatTranscriptionMetrics(
            TranscriptionMetricsSnapshot(
                totalCount = 0L,
                successCount = 0L,
                failureCount = 0L,
                totalBytes = 0L,
                totalDurationMillis = 0L,
                buckets = emptyList(),
            ),
        )

        assertEquals("No voice uploads recorded yet.", text)
    }

    @Test
    fun formatsAverageDurationByBucket() {
        val text = formatTranscriptionMetrics(
            TranscriptionMetricsSnapshot(
                totalCount = 2L,
                successCount = 1L,
                failureCount = 1L,
                totalBytes = 300L * 1024L,
                totalDurationMillis = 3_000L,
                buckets = listOf(
                    TranscriptionMetricsBucket(
                        key = "kb_64_256",
                        label = "64-256 KB",
                        count = 2L,
                        successCount = 1L,
                        totalBytes = 300L * 1024L,
                        totalDurationMillis = 3_000L,
                    ),
                ),
            ),
        )

        assertTrue(text.contains("Average: 1.5 s for 150 KB"))
        assertTrue(text.contains("64-256 KB: 1.5 s avg"))
    }
}
