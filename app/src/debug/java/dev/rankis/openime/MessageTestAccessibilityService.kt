package dev.rankis.openime

import android.view.accessibility.AccessibilityNodeInfo
import dev.rankis.openime.sharing.MessageTranscriptionService

class MessageTestAccessibilityService : MessageTranscriptionService() {
    override fun supports(packageName: String) = packageName == this.packageName
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    fun tapFloatingAction() = tapOverlayAction("messageTranscribeAction")
    fun tapOverlayAction(id: String) = inspectOverlay(id) { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true
    fun overlayBounds(id: String) = inspectOverlay(id) { node ->
        android.graphics.Rect().also { node.getBoundsInScreen(it) }
    }

    private fun <T> inspectOverlay(id: String, inspect: (AccessibilityNodeInfo) -> T): T? {
        for (window in windows) {
            if (window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val root = window.root ?: continue
            val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/$id")
            try { nodes.firstOrNull()?.let { return inspect(it) } }
            finally {
                @Suppress("DEPRECATION") nodes.forEach { it.recycle() }
                @Suppress("DEPRECATION") root.recycle()
            }
        }
        return null
    }

    companion object { @Volatile var instance: MessageTestAccessibilityService? = null; private set }
}
