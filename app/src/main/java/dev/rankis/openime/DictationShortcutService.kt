package dev.rankis.openime

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/** Receives the optional hardware shortcut while another keyboard is selected. */
open class DictationShortcutService : AccessibilityService() {
    private val consumedKeys = mutableSetOf<Pair<Int, Int>>()

    protected open val dictationServiceClass: Class<out RemoteSttInputMethodService>
        get() = RemoteSttInputMethodService::class.java

    override fun onServiceConnected() {
        super.onServiceConnected()
        ShortcutDiagnostics.record(this, "shortcut-service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (event.keyCode != KeyEvent.KEYCODE_SPACE && event.keyCode != KeyEvent.KEYCODE_ENTER &&
            event.keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER
        ) return false
        val key = event.deviceId to event.keyCode
        if (event.action == KeyEvent.ACTION_UP) {
            return consumedKeys.remove(key)
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (key in consumedKeys) return true
        if (event.repeatCount == 0 && (event.isCtrlPressed || event.isMetaPressed)) {
            val action = if (event.keyCode == KeyEvent.KEYCODE_SPACE) "open" else "finish"
            ShortcutDiagnostics.record(this, "$action key ctrl=${event.isCtrlPressed} shift=${event.isShiftPressed} alt=${event.isAltPressed} meta=${event.isMetaPressed}")
        }
        if (event.repeatCount != 0 || !event.isCtrlPressed || !event.isShiftPressed ||
            event.isAltPressed || event.isMetaPressed || event.isCanceled
        ) return false
        val handled = if (event.keyCode == KeyEvent.KEYCODE_SPACE) {
            openDictation()
            true
        } else {
            HardwareDictation.transcribeAndHide()
        }
        if (handled) consumedKeys += key
        return handled
    }

    private fun openDictation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = getSystemService(InputMethodManager::class.java)
        val target = manager.enabledInputMethodList.firstOrNull {
            it.component == ComponentName(this, dictationServiceClass)
        }
        if (target == null) {
            ShortcutDiagnostics.record(this, "open blocked: voice keyboard not enabled")
            Toast.makeText(this, R.string.shortcut_enable_ime, Toast.LENGTH_SHORT).show()
            return
        }
        val previous = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ShortcutDiagnostics.record(this, "open dispatch own-ime-selected=${previous == target.id}")
        val visibility = ShortcutKeyboardVisibility(
            getMode = { softKeyboardController.showMode },
            setMode = { softKeyboardController.setShowMode(it) },
        )
        HardwareDictation.request(
            target.id,
            previous,
            selected = { Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) },
            allowShow = {
                // Older platforms use the IME visibility override without changing system settings.
                val accepted = Build.VERSION.SDK_INT < 36 || visibility.acquire()
                ShortcutDiagnostics.record(this, "hardware-visibility allow=$accepted mode=${softKeyboardController.showMode}")
                accepted
            },
            releaseShow = {
                val restored = visibility.release()
                ShortcutDiagnostics.record(this, "hardware-visibility released=$restored mode=${softKeyboardController.showMode}")
            },
            failed = {
                ShortcutDiagnostics.record(this, "open failed or timed out")
                Toast.makeText(this, R.string.shortcut_open_failed, Toast.LENGTH_SHORT).show()
            },
        ) { id ->
            val accepted = runCatching { softKeyboardController.switchToInputMethod(id) }.getOrDefault(false)
            ShortcutDiagnostics.record(this, "keyboard-switch voice=${id == target.id} accepted=$accepted")
            accepted
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ShortcutDiagnostics.record(this, "shortcut-service unbound")
        HardwareDictation.finish()
        consumedKeys.clear()
        return super.onUnbind(intent)
    }
}
