package dev.rankis.openime

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

class CenteredImePanel(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maximum = (360 * resources.displayMetrics.density).toInt()
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) maximum
            else MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(maximum)
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
    }
}
