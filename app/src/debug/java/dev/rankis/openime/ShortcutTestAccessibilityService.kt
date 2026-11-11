package dev.rankis.openime

import android.view.KeyEvent

class ShortcutTestAccessibilityService : DictationShortcutService() {
    override val dictationServiceClass = SystemImeTestService::class.java

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun dispatch(event: KeyEvent): Boolean = onKeyEvent(event)

    companion object {
        var instance: ShortcutTestAccessibilityService? = null
            private set
    }
}
