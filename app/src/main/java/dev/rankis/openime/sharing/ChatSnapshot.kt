package dev.rankis.openime.sharing

import android.graphics.Bitmap
import android.graphics.Rect

/** Owns a transient image; no persistence or upload path. */
class ChatSnapshot(val bitmap: Bitmap, val bounds: Rect) : AutoCloseable {
    override fun close() { if (!bitmap.isRecycled) bitmap.recycle() }
}
