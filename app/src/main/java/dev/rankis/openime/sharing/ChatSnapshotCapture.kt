package dev.rankis.openime.sharing

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display

class ChatSnapshotCapture(private val service: AccessibilityService) {
    enum class Failure { UNSUPPORTED, PROTECTED, UNAVAILABLE }

    fun capture(windowId: Int, windowBounds: Rect, success: (ChatSnapshot) -> Unit,
                failure: (Failure) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            failure(Failure.UNSUPPORTED)
            return
        }
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val buffer = result.hardwareBuffer
                if (buffer.width.toLong() * buffer.height > 16_777_216L) {
                    buffer.close()
                    failure(Failure.UNAVAILABLE)
                    return
                }
                var hardware: Bitmap? = null
                val image = try {
                    hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                    hardware?.copy(Bitmap.Config.ARGB_8888, false)
                } catch (_: RuntimeException) { null }
                finally { hardware?.recycle(); buffer.close() }
                if (image == null) { failure(Failure.UNAVAILABLE); return }
                val bounds = if (Build.VERSION.SDK_INT >= 34) Rect(windowBounds)
                    else Rect(0, 0, image.width, image.height)
                if (isBlankProtectedContent(image, bounds, windowBounds)) {
                    image.recycle()
                    failure(Failure.PROTECTED)
                } else success(ChatSnapshot(image, bounds))
            }

            override fun onFailure(errorCode: Int) {
                failure(if (Build.VERSION.SDK_INT >= 34 && errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW)
                    Failure.PROTECTED else Failure.UNAVAILABLE)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                service.takeScreenshotOfWindow(windowId, service.mainExecutor, callback)
            } else {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
            }
        } catch (_: RuntimeException) { failure(Failure.UNAVAILABLE) }
    }

    private fun isBlankProtectedContent(image: Bitmap, imageBounds: Rect, windowBounds: Rect): Boolean {
        val content = Rect(windowBounds)
        if (!content.intersect(imageBounds)) return true
        // Exclude system bars; legacy display capture can black out only the secure app.
        val inset = (72 * service.resources.displayMetrics.density).toInt()
        content.inset(0, minOf(inset, content.height() / 4))
        for (row in 0 until 16) for (column in 0 until 16) {
            val screenX = content.left + (content.width() - 1).coerceAtLeast(0) * column / 15
            val screenY = content.top + (content.height() - 1).coerceAtLeast(0) * row / 15
            val x = ((screenX - imageBounds.left).toLong() * image.width / imageBounds.width().coerceAtLeast(1)).toInt().coerceIn(0, image.width - 1)
            val y = ((screenY - imageBounds.top).toLong() * image.height / imageBounds.height().coerceAtLeast(1)).toInt().coerceIn(0, image.height - 1)
            if (image.getPixel(x, y) and 0x00ffffff > 0x00010101) return false
        }
        return true
    }
}
