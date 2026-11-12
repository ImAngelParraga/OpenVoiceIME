package dev.rankis.openime.sharing

import org.junit.Assert.*
import org.junit.Test

class MessageActionPositionTest {
    private val frame = MessageBounds(12, 80, 348, 700)
    private fun place(anchor: MessageBounds?, width: Int = 140) = positionMessageAction(frame, anchor, width, 48, 8, 64)

    @Test fun placesBelowAudioWithoutCoveringIt() {
        val position = place(MessageBounds(20, 120, 260, 190))
        assertEquals(MessageActionPosition(120, 198, true), position)
    }
    @Test fun usesSpaceAboveAudioNearBottom() {
        assertEquals(MessageActionPosition(120, 604, true), place(MessageBounds(20, 660, 260, 695)))
    }
    @Test fun clampsHorizontalPositionInsideWindow() {
        assertEquals(frame.left, place(MessageBounds(0, 120, 100, 190)).x)
        assertEquals(frame.right - 140, place(MessageBounds(260, 120, 600, 190)).x)
    }
    @Test fun missingOrOffscreenAnchorUsesCenteredFallback() {
        val expected = MessageActionPosition(110, 588, false)
        assertEquals(expected, place(null))
        assertEquals(expected, place(MessageBounds(20, -200, 260, -100)))
        assertEquals(expected, place(MessageBounds(20, 120, 20, 190)))
    }
    @Test fun tallMessageFallsBackWhenNeitherSideFits() {
        assertFalse(place(MessageBounds(12, 80, 348, 700)).anchored)
    }
}
