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

    fun tapFloatingAction(): Boolean {
        for (window in windows) {
            if (window.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val root = window.root ?: continue
            val nodes = root.findAccessibilityNodeInfosByText(getString(R.string.message_transcribe))
            try {
                if (nodes.any { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }) return true
            } finally {
                @Suppress("DEPRECATION")
                nodes.forEach { it.recycle() }
                @Suppress("DEPRECATION")
                root.recycle()
            }
        }
        return false
    }
    companion object { @Volatile var instance: MessageTestAccessibilityService? = null; private set }
}
