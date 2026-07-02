package dev.rankis.openime.metrics

import android.content.Context
import androidx.core.content.edit

class TranscriptionMetricsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(audioBytes: Long, durationMillis: Long, success: Boolean) {
        val bucket = sizeBucketFor(audioBytes)
        prefs.edit {
            increment(KEY_TOTAL_COUNT)
            increment(KEY_TOTAL_BYTES, audioBytes.coerceAtLeast(0L))
            increment(KEY_TOTAL_DURATION_MS, durationMillis.coerceAtLeast(0L))
            if (success) {
                increment(KEY_SUCCESS_COUNT)
            } else {
                increment(KEY_FAILURE_COUNT)
            }

            increment(bucket.key.countKey())
            increment(bucket.key.successKey(), if (success) 1L else 0L)
            increment(bucket.key.bytesKey(), audioBytes.coerceAtLeast(0L))
            increment(bucket.key.durationKey(), durationMillis.coerceAtLeast(0L))
        }
    }

    fun load(): TranscriptionMetricsSnapshot {
        return TranscriptionMetricsSnapshot(
            totalCount = prefs.getLong(KEY_TOTAL_COUNT, 0L),
            successCount = prefs.getLong(KEY_SUCCESS_COUNT, 0L),
            failureCount = prefs.getLong(KEY_FAILURE_COUNT, 0L),
            totalBytes = prefs.getLong(KEY_TOTAL_BYTES, 0L),
            totalDurationMillis = prefs.getLong(KEY_TOTAL_DURATION_MS, 0L),
            buckets = TRANSCRIPTION_SIZE_BUCKETS.map { bucket ->
                TranscriptionMetricsBucket(
                    key = bucket.key,
                    label = bucket.label,
                    count = prefs.getLong(bucket.key.countKey(), 0L),
                    successCount = prefs.getLong(bucket.key.successKey(), 0L),
                    totalBytes = prefs.getLong(bucket.key.bytesKey(), 0L),
                    totalDurationMillis = prefs.getLong(bucket.key.durationKey(), 0L),
                )
            },
        )
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun android.content.SharedPreferences.Editor.increment(key: String, by: Long = 1L) {
        putLong(key, prefs.getLong(key, 0L) + by)
    }

    private fun String.countKey(): String = "bucket_${this}_count"
    private fun String.successKey(): String = "bucket_${this}_success"
    private fun String.bytesKey(): String = "bucket_${this}_bytes"
    private fun String.durationKey(): String = "bucket_${this}_duration_ms"

    private companion object {
        const val PREFS_NAME = "openime_transcription_metrics"
        const val KEY_TOTAL_COUNT = "total_count"
        const val KEY_SUCCESS_COUNT = "success_count"
        const val KEY_FAILURE_COUNT = "failure_count"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_TOTAL_DURATION_MS = "total_duration_ms"
    }
}
