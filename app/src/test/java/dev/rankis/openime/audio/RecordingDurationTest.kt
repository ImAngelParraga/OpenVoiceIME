package dev.rankis.openime.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingDurationTest {
    @Test
    fun activeDurationExcludesEveryPausedInterval() {
        var now = 0L
        val duration = RecordingDuration { now }
        duration.start()
        now = 2_000L
        duration.pause()
        now = 30_000L
        assertEquals(2_000L, duration.elapsedMillis)
        duration.resume()
        now = 33_000L
        duration.pause()
        now = 90_000L
        assertEquals(5_000L, duration.elapsedMillis)
        duration.resume()
        now = 91_000L
        assertEquals(6_000L, duration.elapsedMillis)
    }

    @Test
    fun freshRecordingResetsAccumulatedAndPausedTime() {
        var now = 0L
        val duration = RecordingDuration { now }
        duration.start()
        now = 5_000L
        duration.pause()
        now = 100_000L
        duration.start()
        assertEquals(0L, duration.elapsedMillis)
        now += 1_000L
        assertEquals(1_000L, duration.elapsedMillis)
    }
}
