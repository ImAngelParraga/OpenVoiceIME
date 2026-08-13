package dev.rankis.openime

/** Snapshot of the editor state used to make one gesture deterministic. */
internal data class EditorTextSnapshot(
    val text: String,
    val selection: EditorSelection,
) {
    init {
        require(selection.start >= 0)
        require(selection.end >= selection.start)
        require(selection.end <= text.length)
    }
}

internal data class EditorSelection(
    val start: Int,
    val end: Int,
) {
    init {
        require(start >= 0)
        require(end >= start)
    }
}

internal enum class EditorGestureKind {
    Cursor,
    Delete,
}

internal sealed class EditorGestureCommand {
    data class MoveCaret(
        val position: Int,
        val expected: EditorTextSnapshot,
    ) : EditorGestureCommand()

    data class DeleteRange(
        val start: Int,
        val end: Int,
        val expected: EditorTextSnapshot,
    ) : EditorGestureCommand()

    data object NoOp : EditorGestureCommand()
}

/**
 * Pure touch-to-editor math. It never mutates editor state; callers apply the returned command on UP.
 */
internal class EditorGestureController(
    private val kind: EditorGestureKind,
    private val horizontalStepPixels: Float,
    private val touchSlopPixels: Float,
) {
    private var snapshot: EditorTextSnapshot? = null
    private var downX = 0f
    private var downY = 0f
    private var dragStarted = false
    private var cancelled = false

    fun begin(snapshot: EditorTextSnapshot, x: Float, y: Float): Boolean {
        this.snapshot = snapshot
        downX = x
        downY = y
        dragStarted = false
        cancelled = false
        return true
    }

    fun move(x: Float, y: Float) {
        if (snapshot == null || cancelled) {
            return
        }
        val dx = x - downX
        val dy = y - downY
        if (kotlin.math.abs(dy) > touchSlopPixels && kotlin.math.abs(dy) >= kotlin.math.abs(dx)) {
            cancelled = true
            return
        }
        if (kotlin.math.abs(dx) > touchSlopPixels) {
            dragStarted = true
        }
    }

    fun finish(x: Float, y: Float): EditorGestureCommand {
        move(x, y)
        val initial = snapshot ?: return EditorGestureCommand.NoOp
        val result = if (cancelled) {
            EditorGestureCommand.NoOp
        } else {
            when (kind) {
                EditorGestureKind.Cursor -> finishCursor(initial, x)
                EditorGestureKind.Delete -> finishDelete(initial, x)
            }
        }
        reset()
        return result
    }

    fun cancel() {
        reset()
    }

    private fun finishCursor(initial: EditorTextSnapshot, x: Float): EditorGestureCommand {
        if (!dragStarted) {
            return EditorGestureCommand.NoOp
        }
        val dx = x - downX
        val direction = if (dx < 0f) -1 else 1
        val steps = horizontalSteps(dx)
        if (steps == 0) {
            return EditorGestureCommand.NoOp
        }
        val base = if (direction < 0) initial.selection.start else initial.selection.end
        val target = moveByCodePoints(initial.text, base, direction, steps)
        return EditorGestureCommand.MoveCaret(target, initial)
    }

    private fun finishDelete(initial: EditorTextSnapshot, x: Float): EditorGestureCommand {
        if (!dragStarted) {
            return deleteTap(initial)
        }
        val dx = x - downX
        if (dx >= 0f) {
            return EditorGestureCommand.NoOp
        }
        val steps = horizontalSteps(dx)
        if (steps == 0) {
            return EditorGestureCommand.NoOp
        }
        val end = initial.selection.end
        val start = moveByCodePoints(initial.text, end, -1, steps)
        if (start >= end) {
            return EditorGestureCommand.NoOp
        }
        return EditorGestureCommand.DeleteRange(start, end, initial)
    }

    private fun deleteTap(initial: EditorTextSnapshot): EditorGestureCommand {
        val selection = initial.selection
        if (selection.start < selection.end) {
            return EditorGestureCommand.DeleteRange(selection.start, selection.end, initial)
        }
        if (selection.end == 0) {
            return EditorGestureCommand.NoOp
        }
        val caret = codePointBoundary(initial.text, selection.end)
        val start = moveByCodePoints(initial.text, caret, -1, 1)
        return if (start < caret) {
            EditorGestureCommand.DeleteRange(start, caret, initial)
        } else {
            EditorGestureCommand.NoOp
        }
    }

    private fun horizontalSteps(dx: Float): Int {
        val step = horizontalStepPixels.coerceAtLeast(1f)
        return (kotlin.math.abs(dx) / step).toInt()
    }

    private fun moveByCodePoints(text: String, position: Int, direction: Int, steps: Int): Int {
        var result = codePointBoundary(text, position)
        repeat(steps) {
            result = if (direction < 0) {
                if (result == 0) 0 else text.offsetByCodePoints(result, -1)
            } else {
                if (result == text.length) text.length else text.offsetByCodePoints(result, 1)
            }
        }
        return result
    }

    private fun codePointBoundary(text: String, position: Int): Int {
        val bounded = position.coerceIn(0, text.length)
        return if (
            bounded > 0 &&
            bounded < text.length &&
            Character.isLowSurrogate(text[bounded]) &&
            Character.isHighSurrogate(text[bounded - 1])
        ) {
            bounded - 1
        } else {
            bounded
        }
    }

    private fun reset() {
        snapshot = null
        dragStarted = false
        cancelled = false
    }

    companion object {
        fun deleteTap(snapshot: EditorTextSnapshot): EditorGestureCommand {
            return EditorGestureController(
                kind = EditorGestureKind.Delete,
                horizontalStepPixels = 1f,
                touchSlopPixels = Float.MAX_VALUE,
            ).run {
                begin(snapshot, 0f, 0f)
                finish(0f, 0f)
            }
        }
    }
}
