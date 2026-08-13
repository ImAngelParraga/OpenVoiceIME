package dev.rankis.openime

import kotlin.math.abs

internal enum class CursorTrackpadAxis {
    Horizontal,
    Vertical,
}

internal data class CursorTrackpadUpdate(
    val axis: CursorTrackpadAxis,
    val stepDelta: Int,
)

/** Pure dominant-axis cursor gesture math shared by rich and terminal editors. */
internal class CursorTrackpadController(
    private val horizontalStepPixels: Float,
    private val verticalStepPixels: Float,
    private val touchSlopPixels: Float,
    private val maxSteps: Int = 64,
) {
    private var downX = 0f
    private var downY = 0f
    private var lastSteps = 0
    private var active = false

    var axis: CursorTrackpadAxis? = null
        private set

    fun begin(x: Float, y: Float) {
        downX = x
        downY = y
        lastSteps = 0
        axis = null
        active = true
    }

    /** Returns only newly crossed steps; reversal therefore emits opposite deltas. */
    fun move(x: Float, y: Float): CursorTrackpadUpdate? {
        if (!active) return null
        val dx = x - downX
        val dy = y - downY
        if (axis == null) {
            if (maxOf(abs(dx), abs(dy)) < touchSlopPixels) return null
            axis = if (abs(dx) >= abs(dy)) CursorTrackpadAxis.Horizontal else CursorTrackpadAxis.Vertical
        }
        val lockedAxis = axis ?: return null
        val distance = if (lockedAxis == CursorTrackpadAxis.Horizontal) dx else dy
        val stepPixels = if (lockedAxis == CursorTrackpadAxis.Horizontal) {
            horizontalStepPixels
        } else {
            verticalStepPixels
        }.coerceAtLeast(1f)
        val targetSteps = (distance / stepPixels).toInt().coerceIn(-maxSteps, maxSteps)
        val delta = targetSteps - lastSteps
        lastSteps = targetSteps
        return CursorTrackpadUpdate(lockedAxis, delta)
    }

    fun cancel() {
        active = false
        axis = null
        lastSteps = 0
    }
}
