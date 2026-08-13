package dev.rankis.openime

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalGestureControllerTest {
    @Test fun cursorDeltaTracksStepsAndReversalWithoutDuplicates() {
        val c = TerminalGestureController(10f)
        c.begin(100f)
        assertEquals(-3, c.cursorDelta(65f))
        assertEquals(0, c.cursorDelta(65f))
        assertEquals(2, c.cursorDelta(85f))
    }

    @Test fun deleteTapZeroAndDragDefersThenShrinks() {
        val c = TerminalGestureController(10f)
        c.begin(100f)
        assertEquals(0, c.deleteCount(100f))
        assertEquals(4, c.deleteCount(55f))
        assertEquals(1, c.deleteCount(85f))
    }

    @Test fun cancelAndClamp() {
        val c = TerminalGestureController(10f, maxSteps = 3)
        c.begin(0f)
        assertEquals(3, c.deleteCount(-100f))
        c.cancel()
        assertEquals(0, c.deleteCount(-100f))
    }
}
