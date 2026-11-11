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
    private var consumedDevice: Int? = null

    protected open val dictationServiceClass: Class<out RemoteSttInputMethodService>
        get() = RemoteSttInputMethodService::class.java

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (event.keyCode != KeyEvent.KEYCODE_SPACE) return false
        if (event.action == KeyEvent.ACTION_UP) {
            if (consumedDevice != event.deviceId) return false
            consumedDevice = null
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (consumedDevice == event.deviceId) return true
        if (event.repeatCount != 0 || !event.isCtrlPressed || !event.isShiftPressed ||
            event.isAltPressed || event.isMetaPressed || event.isCanceled
        ) return false
        consumedDevice = event.deviceId
        openDictation()
        return true
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
        consumedDevice = null
        return super.onUnbind(intent)
    }
}
