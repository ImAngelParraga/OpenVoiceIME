package dev.rankis.openime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class AudioLevelView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ime_accent)
        strokeCap = Paint.Cap.ROUND
    }
    private val peaks = floatArrayOf(.25f, .5f, .8f, .4f, 1f, .6f, .35f, .9f, .55f, .75f, .3f)
    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = width.toFloat() / peaks.size
        paint.strokeWidth = (2f * resources.displayMetrics.density).coerceAtMost(step / 2)
        val baseline = paint.strokeWidth
        peaks.forEachIndexed { index, peak ->
            val barHeight = baseline + (height - baseline) * peak * progress / 100f
            val x = step * (index + .5f)
            canvas.drawLine(x, (height - barHeight) / 2, x, (height + barHeight) / 2, paint)
        }
    }
}
