package dev.rankis.openime

/** Pure horizontal gesture math for editors that expose no extracted text. */
internal class TerminalGestureController(
    private val horizontalStepPixels: Float,
    private val maxSteps: Int = 64,
) {
    private var downX = 0f
    private var lastSteps = 0
    private var cancelled = false

    fun begin(x: Float) {
        downX = x
        lastSteps = 0
        cancelled = false
    }

    /** Returns newly crossed signed cursor steps since the previous move. */
    fun cursorDelta(x: Float): Int {
        if (cancelled) return 0
        val target = signedSteps(x - downX)
        val delta = target - lastSteps
        lastSteps = target
        return delta
    }

    /** Returns final bounded delete count. Rightward motion shrinks selection. */
    fun deleteCount(x: Float): Int {
        if (cancelled) return 0
        return (-signedSteps(x - downX)).coerceAtLeast(0)
    }

    fun cancel() {
        cancelled = true
    }

    private fun signedSteps(distance: Float): Int {
        val step = horizontalStepPixels.coerceAtLeast(1f)
        return (distance / step).toInt().coerceIn(-maxSteps, maxSteps)
    }
}
