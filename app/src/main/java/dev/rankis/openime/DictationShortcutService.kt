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
            Toast.makeText(this, R.string.shortcut_enable_ime, Toast.LENGTH_SHORT).show()
            return
        }
        val previous = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        HardwareDictation.request(
            target.id,
            previous,
            selected = { Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) },
            failed = { Toast.makeText(this, R.string.shortcut_open_failed, Toast.LENGTH_SHORT).show() },
        ) { id ->
            runCatching { softKeyboardController.switchToInputMethod(id) }.getOrDefault(false)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        HardwareDictation.finish()
        consumedKeys.clear()
        return super.onUnbind(intent)
    }
}
