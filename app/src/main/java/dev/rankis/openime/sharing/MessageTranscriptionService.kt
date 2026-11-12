package dev.rankis.openime.sharing

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import android.view.ContextThemeWrapper
import android.view.WindowManager
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
    private lateinit var overlay: MessageShareOverlay
    private var messageAnchor: MessageBounds? = null
    private var anchorPackage: String? = null
    private var receiverOpened = false
    private var coverDrawn = false
    private var captureGeneration = 0L
    private val receiverReady: () -> Unit = {
        if (::overlay.isInitialized && overlay.isCovering) {
            receiverOpened = true
            captureGeneration++
            automation.stop()
            handler.removeCallbacks(timeout)
            overlay.hideCover()
        }
    }
    private var selectedPackage: String? = null
    private var updateScheduled = false
    private val update = Runnable { updateScheduled = false; inspectWindow() }
    private val timeout = Runnable {
        if (receiverOpened) overlay.hideCover() else fail()
    }
    private val moreLabels = setOf("more options", "more", "más opciones", "más", "otras opciones")
    private val shareLabels = setOf("share", "compartir")
    private val forwardLabels = setOf("forward", "reenviar")
    private val deleteLabels = setOf("delete", "eliminar", "borrar")

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = MessageShareOverlay(ContextThemeWrapper(withAppLocale(), R.style.Theme_OpenVoiceIME),
            getSystemService(WindowManager::class.java))
        ShareHandoff.receiverReady = receiverReady
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::overlay.isInitialized) return
        cancelSharing()
        selectedPackage = null
        messageAnchor = null
        anchorPackage = null
        overlay = MessageShareOverlay(ContextThemeWrapper(withAppLocale(), R.style.Theme_OpenVoiceIME),
            getSystemService(WindowManager::class.java))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::overlay.isInitialized) return
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) messageAnchor = null
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED && supports(event.packageName?.toString().orEmpty())) {
            messageAnchor = null
            anchorPackage = null
            val source = event.source
            if (source != null) {
                try {
                    val bounds = Rect()
                    source.getBoundsInScreen(bounds)
                    val density = resources.displayMetrics.density
                    if (bounds.width() >= 96 * density && bounds.height() >= 32 * density &&
                        bounds.height() <= resources.displayMetrics.heightPixels * 0.4) {
                        messageAnchor = MessageBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                        anchorPackage = event.packageName.toString()
                    }
                } finally { @Suppress("DEPRECATION") source.recycle() }
            }
        }
        // Inspect only the active tree; never retain or log event text or chat contents.
        if (!updateScheduled) {
            updateScheduled = true
            handler.postDelayed(update, 80L)
        }
    }

    protected fun inspectWindow() {
        if (automation.stage != ShareAutomation.Stage.IDLE && !coverDrawn) return
        val root = rootInActiveWindow ?: run { hideButton(); return }
        try {
            val packageName = root.packageName?.toString().orEmpty()
            if (automation.stage == ShareAutomation.Stage.IDLE && !supports(packageName)) {
                hideButton()
                messageAnchor = null
                anchorPackage = null
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
                    val target = findAction(nodes, setOf(getString(R.string.share_target).lowercase(Locale.ROOT)), menuOnly = false,
                        allowOccluded = overlay.isCovering && isResolver(packageName))
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
                        if (receiver) {
                            receiverOpened = true
                        } else {
                            handler.removeCallbacks(timeout)
                            overlay.hideCover()
                            showFailure()
                        }
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
                if (selected && !overlay.isCovering) {
                    val frame = Rect().also { root.getBoundsInScreen(it) }
                    val density = resources.displayMetrics.density
                    val selectedBounds = nodes.firstNotNullOfOrNull { node ->
                        if (!node.isSelected || !node.isVisibleToUser) return@firstNotNullOfOrNull null
                        val bounds = Rect().also { node.getBoundsInScreen(it) }
                        if (bounds.width() < 96 * density || bounds.height() < 32 * density ||
                            bounds.height() > frame.height() * 0.4 || bounds.top < frame.top + 64 * density) null
                        else MessageBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    }
                    val anchor = selectedBounds ?: messageAnchor.takeIf { anchorPackage == packageName }
                    selectedPackage = packageName
                    overlay.showAction(frame, anchor) { beginSharing() }
                } else if (!selected) {
                    hideButton()
                    messageAnchor = null
                    anchorPackage = null
                }
            } finally { recycleNodes(nodes, root) }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun beginSharing() {
        val origin = selectedPackage ?: return
        val source = rootInActiveWindow ?: return
        val windowId: Int
        val frame = Rect()
        try {
            if (source.packageName?.toString() != origin) return
            windowId = source.windowId
            val window = source.window
            if (window != null) {
                try { window.getBoundsInScreen(frame) }
                finally { @Suppress("DEPRECATION") window.recycle() }
            } else source.getBoundsInScreen(frame)
        } finally { @Suppress("DEPRECATION") source.recycle() }
        hideButton()
        receiverOpened = false
        coverDrawn = false
        val generation = ++captureGeneration
        automation.start(origin, SystemClock.uptimeMillis())
        handler.postDelayed(timeout, 8_000L)
        // Remove the action from legacy full-display capture before taking the snapshot.
        handler.postDelayed({
            if (generation != captureGeneration) return@postDelayed
            if (!sameSource(origin, windowId)) { cancelSharing(); return@postDelayed }
            captureChat(windowId, frame, success = { snapshot ->
                if (generation != captureGeneration || !sameSource(origin, windowId)) {
                    snapshot.close()
                    if (generation == captureGeneration) cancelSharing()
                    return@captureChat
                }
                if (!overlay.showSnapshot(snapshot, onCancel = { cancelSharing() }, onDrawn = {
                    if (generation == captureGeneration) {
                        coverDrawn = true
                        inspectWindow()
                    }
                })) fail()
            }, failure = { reason ->
                if (generation == captureGeneration) {
                    cancelSharing()
                    val message = when (reason) {
                        ChatSnapshotCapture.Failure.UNSUPPORTED -> R.string.share_snapshot_unsupported
                        ChatSnapshotCapture.Failure.PROTECTED -> R.string.share_snapshot_protected
                        ChatSnapshotCapture.Failure.UNAVAILABLE -> R.string.share_snapshot_unavailable
                    }
                    Toast.makeText(withAppLocale(), message, Toast.LENGTH_LONG).show()
                }
            })
        }, 80L)
    }

    protected open fun captureChat(windowId: Int, frame: Rect, success: (ChatSnapshot) -> Unit,
                                   failure: (ChatSnapshotCapture.Failure) -> Unit) {
        ChatSnapshotCapture(this).capture(windowId, frame, success, failure)
    }

    private fun sameSource(packageName: String, windowId: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        return try { root.packageName?.toString() == packageName && root.windowId == windowId }
            finally { @Suppress("DEPRECATION") root.recycle() }
    }

    private fun cancelSharing() {
        captureGeneration++
        automation.stop()
        handler.removeCallbacks(update)
        handler.removeCallbacks(timeout)
        updateScheduled = false
        overlay.clear()
    }

    private fun hideButton() {
        if (::overlay.isInitialized) overlay.hideAction()
        selectedPackage = null
    }

    private fun fail() {
        captureGeneration++
        automation.stop()
        handler.removeCallbacks(timeout)
        hideButton()
        overlay.hideCover()
        showFailure()
    }

    private fun showFailure() {
        val localized = withAppLocale()
        Toast.makeText(localized, R.string.message_share_failed, Toast.LENGTH_LONG).show()
    }

    private fun findAction(nodes: List<AccessibilityNodeInfo>, labels: Set<String>,
                           menuOnly: Boolean = true, allowOccluded: Boolean = false): AccessibilityNodeInfo? =
        nodes.firstOrNull { node ->
            (node.isVisibleToUser || allowOccluded) && node.isEnabled && (!menuOnly || isMenuControl(node)) &&
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
    override fun onUnbind(intent: android.content.Intent?): Boolean { clear(disconnected = true); return super.onUnbind(intent) }
    override fun onDestroy() { clear(disconnected = true); super.onDestroy() }
    private fun clear(disconnected: Boolean = false) {
        captureGeneration++
        handler.removeCallbacksAndMessages(null)
        updateScheduled = false
        automation.stop()
        if (::overlay.isInitialized) overlay.clear()
        selectedPackage = null
        messageAnchor = null
        anchorPackage = null
        if (disconnected && ShareHandoff.receiverReady === receiverReady) ShareHandoff.receiverReady = null
    }
}
