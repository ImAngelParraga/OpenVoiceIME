package dev.rankis.openime

/** Snapshot of the editor state used to make one gesture deterministic. */
internal data class EditorTextSnapshot(
    val text: String,
    val selection: EditorSelection,
    val startOffset: Int = 0,
) {
    init {
        require(selection.start >= 0)
        require(selection.end >= selection.start)
        require(selection.end <= text.length)
        require(startOffset >= 0)
    }

    fun globalPosition(localPosition: Int): Int = startOffset + localPosition.coerceIn(0, text.length)
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
    data class PreviewSelection(
        val selection: EditorSelection,
        val expected: EditorTextSnapshot,
    ) : EditorGestureCommand()

    data class RestoreSelection(
        val selection: EditorSelection,
        val expected: EditorTextSnapshot,
    ) : EditorGestureCommand()

    data class MoveCaret(
        val position: Int,
        val expected: EditorTextSnapshot,
    ) : EditorGestureCommand()

    data class DeleteRange(
        val start: Int,
        val end: Int,
        val expected: EditorTextSnapshot,
        val fromTap: Boolean = false,
    ) : EditorGestureCommand()

    data object NoOp : EditorGestureCommand()
}

/**
 * Pure touch-to-editor math. It never mutates editor state; callers apply previews on MOVE and
 * destructive/final commands on UP.
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
    private var previewSelection: EditorSelection? = null

    fun begin(snapshot: EditorTextSnapshot, x: Float, y: Float): Boolean {
        this.snapshot = snapshot
        downX = x
        downY = y
        dragStarted = false
        cancelled = false
        previewSelection = snapshot.selection
        return true
    }

    fun move(x: Float, y: Float): EditorGestureCommand {
        if (snapshot == null || cancelled) {
            return EditorGestureCommand.NoOp
        }
        val dx = x - downX
        val dy = y - downY
        if (kotlin.math.abs(dy) > touchSlopPixels && kotlin.math.abs(dy) >= kotlin.math.abs(dx)) {
            cancelled = true
            val initial = snapshot ?: return EditorGestureCommand.NoOp
            previewSelection = initial.selection
            return EditorGestureCommand.RestoreSelection(initial.selection, initial)
        }
        if (kotlin.math.abs(dx) > touchSlopPixels) {
            dragStarted = true
        }
        if (!dragStarted) {
            return EditorGestureCommand.NoOp
        }
        val initial = snapshot ?: return EditorGestureCommand.NoOp
        val selection = previewFor(initial, dx)
        previewSelection = selection
        return EditorGestureCommand.PreviewSelection(selection, initial)
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
        val selection = previewSelection ?: previewFor(initial, x - downX)
        if (selection.start != selection.end) {
            return EditorGestureCommand.NoOp
        }
        return if (selection == initial.selection) {
            EditorGestureCommand.NoOp
        } else {
            EditorGestureCommand.MoveCaret(selection.start, initial)
        }
    }

    private fun finishDelete(initial: EditorTextSnapshot, x: Float): EditorGestureCommand {
        if (!dragStarted) {
            return deleteTap(initial)
        }
        val selection = previewSelection ?: previewFor(initial, x - downX)
        if (selection.start >= selection.end) {
            return EditorGestureCommand.NoOp
        }
        return EditorGestureCommand.DeleteRange(selection.start, selection.end, initial)
    }

    private fun previewFor(initial: EditorTextSnapshot, dx: Float): EditorSelection {
        return when (kind) {
            EditorGestureKind.Cursor -> {
                val direction = if (dx < 0f) -1 else 1
                val steps = horizontalSteps(dx)
                val base = if (direction < 0) initial.selection.start else initial.selection.end
                val target = moveByCodePoints(initial.text, base, direction, steps)
                EditorSelection(target, target)
            }
            EditorGestureKind.Delete -> {
                val end = initial.selection.end
                if (dx < 0f) {
                    val start = moveByCodePoints(initial.text, end, -1, horizontalSteps(dx))
                    EditorSelection(start, end)
                } else {
                    EditorSelection(end, end)
                }
            }
        }
    }

    private fun deleteTap(initial: EditorTextSnapshot): EditorGestureCommand {
        val selection = initial.selection
        if (selection.start < selection.end) {
            return EditorGestureCommand.DeleteRange(selection.start, selection.end, initial, fromTap = true)
        }
        if (selection.end == 0) {
            return EditorGestureCommand.NoOp
        }
        val caret = codePointBoundary(initial.text, selection.end)
        val start = moveByCodePoints(initial.text, caret, -1, 1)
        return if (start < caret) {
            EditorGestureCommand.DeleteRange(start, caret, initial, fromTap = true)
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
        previewSelection = null
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
