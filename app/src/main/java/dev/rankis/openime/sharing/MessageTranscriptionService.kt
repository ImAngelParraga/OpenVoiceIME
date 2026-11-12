package dev.rankis.openime.sharing

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.Toast
import dev.rankis.openime.R
import dev.rankis.openime.settings.withAppLocale
import java.util.ArrayDeque
import java.util.Locale

open class MessageTranscriptionService : AccessibilityService() {
    protected open fun supports(packageName: String) = packageName in setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
    )
    protected open fun isResolver(packageName: String) = packageName in setOf(
        "android", "com.android.intentresolver", "com.google.android.intentresolver",
    )
    private val handler = Handler(Looper.getMainLooper())
    private val automation = ShareAutomation()
    private var button: Button? = null
    private var selectedPackage: String? = null
    private var updateScheduled = false
    private val update = Runnable { updateScheduled = false; inspectWindow() }
    private val timeout = Runnable { fail() }
    private val moreLabels = setOf("more options", "more", "más opciones", "más", "otras opciones")
    private val shareLabels = setOf("share", "compartir")
    private val forwardLabels = setOf("forward", "reenviar")
    private val deleteLabels = setOf("delete", "eliminar", "borrar")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Inspect only the active tree; never retain or log event text or chat contents.
        if (!updateScheduled) {
            updateScheduled = true
            handler.postDelayed(update, 80L)
        }
    }

    protected fun inspectWindow() {
        val root = rootInActiveWindow ?: run { hideButton(); return }
        try {
            val packageName = root.packageName?.toString().orEmpty()
            if (automation.stage == ShareAutomation.Stage.IDLE && !supports(packageName)) {
                hideButton()
                return
            }
            val receiverNodes = if (packageName == this.packageName)
                root.findAccessibilityNodeInfosByViewId("${this.packageName}:id/shareClose") else emptyList()
            val receiver = packageName == this.packageName && receiverNodes.isNotEmpty()
            @Suppress("DEPRECATION")
            receiverNodes.forEach { it.recycle() }
            if (automation.stage != ShareAutomation.Stage.IDLE) {
                val nodes = collectNodes(root)
                try {
                    val more = findAction(nodes, moreLabels)
                    val share = findAction(nodes, shareLabels)
                    val target = findAction(nodes, setOf(getString(R.string.share_target).lowercase(Locale.ROOT)), menuOnly = false)
                    val scroll = nodes.firstOrNull { it.isVisibleToUser && it.isScrollable &&
                        it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } }
                    val confirm = findAction(nodes, setOf("just once", "solo una vez", "sólo una vez", "una vez"))
                    val action = automation.next(packageName, SystemClock.uptimeMillis(), more != null,
                        share != null, target != null, isResolver(packageName), receiver, scroll != null, confirm != null)
                    val node = when (action) {
                        ShareAutomation.Action.MORE -> more
                        ShareAutomation.Action.SHARE -> share
                        ShareAutomation.Action.TARGET -> target
                        ShareAutomation.Action.CONFIRM -> confirm
                        else -> null
                    }
                    if (action == ShareAutomation.Action.STOP) {
                        handler.removeCallbacks(timeout)
                        if (!receiver) showFailure()
                    } else if (action == ShareAutomation.Action.SCROLL && scroll != null) {
                        scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        handler.postDelayed(update, 500L)
                    } else if (node != null) {
                        if (!click(node)) fail() else handler.postDelayed(update, 180L)
                    }
                } finally { recycleNodes(nodes, root) }
                return
            }
            if (!supports(packageName)) { hideButton(); return }
            val nodes = collectNodes(root)
            try {
                // Both apps expose these actions only in their message-selection toolbar.
                val selected = findAction(nodes, deleteLabels) != null &&
                    (findAction(nodes, forwardLabels) != null || findAction(nodes, shareLabels) != null)
                if (selected) showButton(packageName) else hideButton()
            } finally { recycleNodes(nodes, root) }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun showButton(packageName: String) {
        selectedPackage = packageName
        if (button != null) return
        val localized = withAppLocale()
        val action = Button(localized).apply {
            text = localized.getString(R.string.message_transcribe)
            contentDescription = localized.getString(R.string.message_transcribe_description)
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setOnClickListener { beginSharing() }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (16 * resources.displayMetrics.density).toInt()
            y = (96 * resources.displayMetrics.density).toInt()
        }
        if (runCatching { getSystemService(WindowManager::class.java).addView(action, params) }.isSuccess) button = action
    }

    private fun beginSharing() {
        val origin = selectedPackage ?: return
        hideButton()
        automation.start(origin, SystemClock.uptimeMillis())
        handler.postDelayed(timeout, 8_000L)
        inspectWindow()
    }

    private fun hideButton() {
        button?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        button = null
        selectedPackage = null
    }

    private fun fail() {
        automation.stop()
        handler.removeCallbacks(timeout)
        hideButton()
        showFailure()
    }

    private fun showFailure() {
        val localized = withAppLocale()
        Toast.makeText(localized, R.string.message_share_failed, Toast.LENGTH_LONG).show()
    }

    private fun findAction(nodes: List<AccessibilityNodeInfo>, labels: Set<String>,
                           menuOnly: Boolean = true): AccessibilityNodeInfo? =
        nodes.firstOrNull { node ->
            node.isVisibleToUser && node.isEnabled && (!menuOnly || isMenuControl(node)) &&
                sequenceOf(node.text, node.contentDescription).any { value ->
                    value?.toString()?.trim()?.lowercase(Locale.ROOT) in labels
                } && canClick(node)
        }

    private fun isMenuControl(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName.orEmpty().substringAfter(":id/")
        val type = node.className?.toString().orEmpty()
        return id.startsWith("menu") || id.startsWith("action_") || id == "title" ||
            type.endsWith("Button") || type.endsWith("ActionBarMenuItem")
    }

    private fun canClick(node: AccessibilityNodeInfo) = withClickable(node) { true }
    private fun click(node: AccessibilityNodeInfo) = withClickable(node) {
        it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun withClickable(node: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Boolean): Boolean {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            val candidate = current ?: return false
            if (candidate.isClickable && candidate.isEnabled) {
                return try { action(candidate) } finally {
                    if (candidate !== node) { @Suppress("DEPRECATION") candidate.recycle() }
                }
            }
            current = candidate.parent
            if (candidate !== node) { @Suppress("DEPRECATION") candidate.recycle() }
        }
        current?.let { @Suppress("DEPRECATION") it.recycle() }
        return false
    }

    private fun collectNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (pending.isNotEmpty() && nodes.size < 600) {
            val node = pending.removeFirst()
            nodes += node
            val remaining = (600 - nodes.size - pending.size).coerceAtLeast(0)
            for (index in 0 until minOf(node.childCount, 100, remaining)) node.getChild(index)?.let { pending.add(it) }
        }
        @Suppress("DEPRECATION")
        pending.forEach { it.recycle() }
        return nodes
    }

    private fun recycleNodes(nodes: List<AccessibilityNodeInfo>, root: AccessibilityNodeInfo) {
        @Suppress("DEPRECATION")
        nodes.filter { it !== root }.forEach { it.recycle() }
    }

    override fun onInterrupt() { clear() }
    override fun onUnbind(intent: android.content.Intent?): Boolean { clear(); return super.onUnbind(intent) }
    override fun onDestroy() { clear(); super.onDestroy() }
    private fun clear() { handler.removeCallbacksAndMessages(null); updateScheduled = false; automation.stop(); hideButton() }
}
