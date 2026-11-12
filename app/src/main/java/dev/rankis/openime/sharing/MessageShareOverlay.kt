package dev.rankis.openime.sharing

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.view.doOnPreDraw
import dev.rankis.openime.R

class MessageShareOverlay(private val context: Context, private val manager: WindowManager) {
    private var action: View? = null
    private var cover: View? = null
    val isCovering get() = cover != null
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun showAction(frame: Rect, anchor: MessageBounds?, onClick: () -> Unit) {
        if (cover != null) return
        val view = action ?: LayoutInflater.from(context).inflate(R.layout.message_transcribe_action, null).also {
            it.setOnClickListener { onClick() }
            action = it
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(frame.width(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(dp(48), View.MeasureSpec.EXACTLY))
        val safe = MessageBounds(frame.left + dp(12), frame.top + dp(64), frame.right - dp(12), frame.bottom - dp(12))
        val position = positionMessageAction(safe, anchor, view.measuredWidth, view.measuredHeight, dp(8), dp(64))
        val params = overlayParams(view.measuredWidth, view.measuredHeight).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = position.x
            y = position.y
        }
        runCatching {
            if (view.parent == null) manager.addView(view, params) else manager.updateViewLayout(view, params)
        }.onFailure { hideAction() }
    }

    fun showCover(onCancel: () -> Unit, onDrawn: () -> Unit): Boolean {
        hideAction()
        if (cover != null) return false
        val view = LayoutInflater.from(context).inflate(R.layout.message_share_progress, null)
        view.findViewById<Button>(R.id.shareCoverCancel).setOnClickListener { onCancel() }
        cover = view
        return runCatching {
            // Wait for a rendered cover before operating the underlying menus.
            view.doOnPreDraw { view.postOnAnimation { if (cover === view) onDrawn() } }
            manager.addView(view, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))
            true
        }.getOrElse { hideCover(); false }
    }

    fun hideAction() { remove(action); action = null }
    fun hideCover() { remove(cover); cover = null }
    fun clear() { hideAction(); hideCover() }

    private fun remove(view: View?) {
        if (view != null) runCatching { manager.removeView(view) }
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(width, height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT)
}
