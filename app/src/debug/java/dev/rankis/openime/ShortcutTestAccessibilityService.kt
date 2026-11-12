package dev.rankis.openime

import android.view.KeyEvent

class ShortcutTestAccessibilityService : DictationShortcutService() {
    override val dictationServiceClass = SystemImeTestService::class.java

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun dispatch(event: KeyEvent): Boolean = onKeyEvent(event)

    fun attachForTest(context: android.content.Context) { attachBaseContext(context) }

    companion object {
        var instance: ShortcutTestAccessibilityService? = null
            private set
    }
}
