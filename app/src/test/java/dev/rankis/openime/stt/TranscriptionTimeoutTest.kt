package dev.rankis.openime.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionTimeoutTest {
    @Test
    fun testV30DeadlineScalesWithRecordingDurationAndHasFiniteBounds() {
        assertEquals(45_000L, transcriptionTimeoutMillis(1_000L, 8_000L))
        assertEquals(150_000L, transcriptionTimeoutMillis(60_000L, 480_000L))
        assertEquals(630_000L, transcriptionTimeoutMillis(300_000L, 2_400_000L))
        assertEquals(900_000L, transcriptionTimeoutMillis(Long.MAX_VALUE, Long.MAX_VALUE))
        assertTrue(transcriptionTimeoutMillis(60_000L, 1L) > transcriptionTimeoutMillis(10_000L, 1L))
    }

    @Test
    fun testV30SizeFallbackUsesRecorderBitrateWithoutOverflow() {
        assertEquals(150_000L, transcriptionTimeoutMillis(0L, 480_000L))
        assertEquals(45_000L, transcriptionTimeoutMillis(-1L, -1L))
        assertEquals(900_000L, transcriptionTimeoutMillis(0L, Long.MAX_VALUE))
        assertEquals(45_000L, transcriptionTimeoutMillis(1_000L, Long.MAX_VALUE))
    }
}
