package dev.rankis.openime.sharing

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/** Wrap short results while keeping long transcripts scrollable above the actions. */
class TranscriptPanelLayout(context: Context, attributes: AttributeSet?) : LinearLayout(context, attributes) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = (resources.displayMetrics.heightPixels * 0.85).toInt()
        val available = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) cap
            else minOf(cap, MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
    }
}
