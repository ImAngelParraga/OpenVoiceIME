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

    var holdCapture = false
    var pendingCapture: ((dev.rankis.openime.sharing.ChatSnapshot) -> Unit)? = null
    var lastSnapshot: dev.rankis.openime.sharing.ChatSnapshot? = null
    var captureFailure: dev.rankis.openime.sharing.ChatSnapshotCapture.Failure? = null

    override fun captureChat(windowId: Int, frame: android.graphics.Rect,
                             success: (dev.rankis.openime.sharing.ChatSnapshot) -> Unit,
                             failure: (dev.rankis.openime.sharing.ChatSnapshotCapture.Failure) -> Unit) {
        if (holdCapture) pendingCapture = success
        else super.captureChat(windowId, frame, { snapshot -> lastSnapshot = snapshot; success(snapshot) }) { reason -> captureFailure = reason; failure(reason) }
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
