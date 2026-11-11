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
        var shown: Boolean = false,
    )

    fun attach(ime: RemoteSttInputMethodService) { service = ime }

    fun detach(ime: RemoteSttInputMethodService) {
        if (service !== ime) return
        finish()
        service = null
    }

    fun request(
        target: String,
        previous: String?,
        selected: () -> String?,
        failed: () -> Unit,
        switch: (String) -> Boolean,
    ) {
        if (session != null) return
        session = Session(target, previous?.takeUnless { it == target }, switch, selected, failed)
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
        if (!owns(ime) || session?.shown != false || ime.currentInputConnection == null) return
        // Let the current input-start callback finish before requesting the window.
        handler.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                owns(ime) && ime.currentInputConnection != null
            ) ime.requestShowSelf(0)
        }
    }

    fun shown(ime: RemoteSttInputMethodService) {
        if (!owns(ime)) return
        session?.shown = true
        handler.removeCallbacks(expire)
    }

    fun hidden(ime: RemoteSttInputMethodService) {
        if (owns(ime) && session?.shown == true) finish()
    }

    fun transcribeAndHide(): Boolean {
        if (session?.shown == false) return true
        val ime = service ?: return session != null
        val selected = Settings.Secure.getString(ime.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isSelected = ComponentName.unflattenFromString(selected.orEmpty()) == ComponentName(ime, ime.javaClass)
        return (isSelected && ime.finishDictationFromKeyboard()) || session != null
    }

    fun finish(restorePrevious: Boolean = true): Boolean {
        val current = session ?: return false
        session = null
        handler.removeCallbacks(expire)
        if (!restorePrevious || current.selected() != current.target) return false
        return current.previous?.let { current.switch(it) } ?: false
    }

    fun finish(ime: RemoteSttInputMethodService, restorePrevious: Boolean = true): Boolean {
        if (!owns(ime)) return false
        return finish(restorePrevious)
    }
}
