package dev.rankis.openime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorGestureControllerTest {
    @Test
    fun cursorSlideMovesByCodePointsAndDoesNotSplitSurrogatePair() {
        val snapshot = EditorTextSnapshot("a😀bc", EditorSelection(5, 5))
        val controller = controller(EditorGestureKind.Cursor)

        controller.begin(snapshot, x = 100f, y = 0f)
        controller.move(x = 75f, y = 0f)
        val command = controller.finish(x = 75f, y = 0f) as EditorGestureCommand.MoveCaret

        assertEquals(3, command.position)
        assertTrue(command.position == 0 || !snapshot.text[command.position].let { Character.isLowSurrogate(it) })
    }

    @Test
    fun cursorSlideClampsAtEditorStartAndEnd() {
        val snapshot = EditorTextSnapshot("abc", EditorSelection(0, 0))
        val controller = controller(EditorGestureKind.Cursor)

        controller.begin(snapshot, x = 100f, y = 0f)
        controller.move(x = 0f, y = 0f)
        val left = controller.finish(x = 0f, y = 0f) as EditorGestureCommand.MoveCaret
        assertEquals(0, left.position)

        val rightController = controller(EditorGestureKind.Cursor)
        rightController.begin(snapshot.copy(selection = EditorSelection(3, 3)), x = 0f, y = 0f)
        rightController.move(x = 100f, y = 0f)
        val right = rightController.finish(x = 100f, y = 0f) as EditorGestureCommand.MoveCaret
        assertEquals(3, right.position)
    }

    @Test
    fun deleteTapDeletesSelectionOrOneCodePoint() {
        val selected = EditorTextSnapshot("hello", EditorSelection(1, 4))
        val selectedCommand = EditorGestureController.deleteTap(selected) as EditorGestureCommand.DeleteRange
        assertEquals(EditorSelection(1, 4), EditorSelection(selectedCommand.start, selectedCommand.end))

        val emoji = EditorTextSnapshot("a😀", EditorSelection(3, 3))
        val emojiCommand = EditorGestureController.deleteTap(emoji) as EditorGestureCommand.DeleteRange
        assertEquals(1, emojiCommand.start)
        assertEquals(3, emojiCommand.end)
    }

    @Test
    fun deleteSlideSelectsBackwardAndRightwardMotionShrinksSelection() {
        val snapshot = EditorTextSnapshot("abcde", EditorSelection(5, 5))
        val controller = controller(EditorGestureKind.Delete)

        controller.begin(snapshot, x = 100f, y = 0f)
        controller.move(x = 55f, y = 0f)
        controller.move(x = 90f, y = 0f)
        val command = controller.finish(x = 90f, y = 0f) as EditorGestureCommand.DeleteRange

        assertEquals(4, command.start)
        assertEquals(5, command.end)
    }

    @Test
    fun verticalEscapeCancelsWithoutDelete() {
        val snapshot = EditorTextSnapshot("abc", EditorSelection(3, 3))
        val controller = controller(EditorGestureKind.Delete)

        controller.begin(snapshot, x = 100f, y = 0f)
        controller.move(x = 70f, y = 40f)

        assertTrue(controller.finish(x = 70f, y = 40f) is EditorGestureCommand.NoOp)
    }

    private fun controller(kind: EditorGestureKind): EditorGestureController {
        return EditorGestureController(
            kind = kind,
            horizontalStepPixels = 10f,
            touchSlopPixels = 2f,
        )
    }
}
