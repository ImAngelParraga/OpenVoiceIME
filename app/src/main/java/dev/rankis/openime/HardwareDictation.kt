package dev.rankis.openime

import android.content.ComponentName
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Main-thread handoff between the shortcut listener and the bound voice keyboard. */
internal object HardwareDictation {
    private val handler = Handler(Looper.getMainLooper())
    private var service: RemoteSttInputMethodService? = null
    private var session: Session? = null
    private val expire = Runnable {
        session?.failed?.invoke()
        finish()
    }

    private data class Session(
        val target: String,
        val previous: String?,
        val switch: (String) -> Boolean,
        val selected: () -> String?,
        val failed: () -> Unit,
        val allowShow: () -> Boolean,
        val releaseShow: () -> Unit,
        var shown: Boolean = false,
        var restorePrevious: Boolean = true,
    )

    private fun trace(event: String) { service?.let { ShortcutDiagnostics.record(it, event) } }

    fun attach(ime: RemoteSttInputMethodService) {
        service = ime
        trace("voice-service attached")
    }

    fun detach(ime: RemoteSttInputMethodService) {
        if (service !== ime) return
        trace("voice-service detached")
        finish()
        service = null
    }

    fun request(
        target: String,
        previous: String?,
        selected: () -> String?,
        failed: () -> Unit,
        allowShow: () -> Boolean = { true },
        releaseShow: () -> Unit = {},
        switch: (String) -> Boolean,
    ) {
        trace("open handoff existing=${session != null} shown=${session?.shown} own-ime-selected=${previous == target}")
        if (session != null) return
        session = Session(target, previous?.takeUnless { it == target }, switch, selected, failed, allowShow, releaseShow)
        handler.postDelayed(expire, 3_000)
        if (previous != target && !switch(target)) {
            failed()
            finish()
            return
        }
        if (previous == target) service?.let { showPending(it) }
    }

    fun owns(ime: RemoteSttInputMethodService): Boolean = service === ime && session?.target?.let {
        ComponentName.unflattenFromString(it) == ComponentName(ime, ime.javaClass)
    } == true

    fun showPending(ime: RemoteSttInputMethodService) {
        trace("show-pending owned=${owns(ime)} shown=${session?.shown} editor=${ime.currentInputConnection != null}")
        if (!owns(ime) || session?.shown != false || ime.currentInputConnection == null) return
        // Let the current input-start callback finish before requesting the window.
        handler.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                owns(ime) && ime.currentInputConnection != null
            ) {
                val current = session ?: return@post
                if (current.selected() != current.target) {
                    finish(restorePrevious = false)
                    return@post
                }
                if (!current.allowShow()) {
                    current.failed()
                    finish()
                    return@post
                }
                trace("requestShowSelf")
                ime.requestShowSelf(0)
            }
        }
    }

    fun shown(ime: RemoteSttInputMethodService) {
        if (!owns(ime)) return
        trace("shortcut input-view started")
        session?.shown = true
        handler.removeCallbacks(expire)
    }

    fun hidden(ime: RemoteSttInputMethodService) {
        trace("shortcut window hidden owned=${owns(ime)} shown=${session?.shown}")
        if (owns(ime) && session?.shown == true) finish()
    }

    fun transcribeAndHide(): Boolean {
        if (session?.shown == false) return true
        val ime = service ?: return session != null
        val selected = Settings.Secure.getString(ime.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isSelected = ComponentName.unflattenFromString(selected.orEmpty()) == ComponentName(ime, ime.javaClass)
        return (isSelected && ime.finishDictationFromKeyboard()) || session != null
    }

    fun prepareHideWithoutKeyboardReturn(ime: RemoteSttInputMethodService) {
        if (owns(ime)) session?.restorePrevious = false
    }

    fun finish(restorePrevious: Boolean = true): Boolean {
        val current = session ?: return false
        trace("handoff finished restore-previous=${restorePrevious && current.restorePrevious}")
        session = null
        handler.removeCallbacks(expire)
        current.releaseShow()
        if (!restorePrevious || !current.restorePrevious || current.selected() != current.target) return false
        return current.previous?.let { current.switch(it) } ?: false
    }

    fun finish(ime: RemoteSttInputMethodService, restorePrevious: Boolean = true): Boolean {
        if (!owns(ime)) return false
        return finish(restorePrevious)
    }
}
