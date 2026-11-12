package dev.rankis.openime.sharing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class FrozenChatView(context: Context, snapshot: ChatSnapshot) : View(context) {
    private var image: ChatSnapshot? = snapshot
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val snapshot = image ?: return
        val location = IntArray(2)
        getLocationOnScreen(location)
        canvas.save()
        canvas.translate(-location[0].toFloat(), -location[1].toFloat())
        canvas.drawBitmap(snapshot.bitmap, null, snapshot.bounds, paint)
        canvas.restore()
    }

    fun release() {
        val owned = image
        image = null
        owned?.close()
    }
}
