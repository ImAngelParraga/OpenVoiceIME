package dev.rankis.openime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CursorTrackpadControllerTest {
    @Test
    fun horizontalAxisLocksAtTieAndReportsOnlyNewSteps() {
        val controller = controller()
        controller.begin(100f, 100f)

        assertNull(controller.move(104f, 104f))
        assertEquals(
            CursorTrackpadUpdate(CursorTrackpadAxis.Horizontal, 2),
            controller.move(125f, 125f),
        )
        assertEquals(
            CursorTrackpadUpdate(CursorTrackpadAxis.Horizontal, 0),
            controller.move(125f, 170f),
        )
    }

    @Test
    fun verticalAxisReportsIncrementalStepsAndReversal() {
        val controller = controller()
        controller.begin(100f, 100f)

        assertEquals(
            CursorTrackpadUpdate(CursorTrackpadAxis.Vertical, 3),
            controller.move(102f, 135f),
        )
        assertEquals(
            CursorTrackpadUpdate(CursorTrackpadAxis.Vertical, -2),
            controller.move(160f, 115f),
        )
        assertEquals(
            CursorTrackpadUpdate(CursorTrackpadAxis.Vertical, -2),
            controller.move(160f, 85f),
        )
    }

    @Test
    fun tapCancelAndMaximumClampEmitNoExtraSteps() {
        val controller = controller(maxSteps = 2)
        controller.begin(0f, 0f)
        assertNull(controller.move(3f, 4f))
        assertEquals(
            CursorTrackpadUpdate(CursorTrackpadAxis.Vertical, 2),
            controller.move(0f, 100f),
        )
        assertEquals(
            CursorTrackpadUpdate(CursorTrackpadAxis.Vertical, 0),
            controller.move(0f, 200f),
        )
        controller.cancel()
        assertNull(controller.move(0f, -200f))
    }

    private fun controller(maxSteps: Int = 64): CursorTrackpadController {
        return CursorTrackpadController(
            horizontalStepPixels = 10f,
            verticalStepPixels = 10f,
            touchSlopPixels = 5f,
            maxSteps = maxSteps,
        )
    }
}
