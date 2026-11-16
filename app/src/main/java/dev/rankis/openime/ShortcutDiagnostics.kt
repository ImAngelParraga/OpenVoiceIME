package dev.rankis.openime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Local event history containing fixed labels and lifecycle flags, never editor or connection data. */
internal object ShortcutDiagnostics {
    private const val PREFERENCES = "openime_shortcut_diagnostics"
    private const val EVENTS = "events"
    private const val MAX_EVENTS = 80

    private const val FLUSH_INTERVAL_MILLIS = 750L
    private val pending = ArrayDeque<String>()
    private val writer = Executors.newSingleThreadScheduledExecutor()
    private var cachedHistory: String? = null

    @Synchronized
    fun record(context: Context, event: String) {
        if (pending.size == MAX_EVENTS) pending.removeFirst()
        pending.addLast("${SystemClock.elapsedRealtime()}ms ${event.take(240)}")
        if (pending.size == 1) {
            val appContext = context.applicationContext
            writer.schedule({ flush(appContext) }, FLUSH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun flush(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val saved = preferences.getString(EVENTS, "").orEmpty()
        val history = synchronized(this) {
            completeHistory(cachedHistory ?: saved).also {
                cachedHistory = it
                pending.clear()
            }
        }
        preferences.edit().putString(EVENTS, history).commit()
    }

    private fun completeHistory(saved: String): String =
        (saved.lineSequence().filter { it.isNotEmpty() }.toList() + pending)
            .takeLast(MAX_EVENTS)
            .joinToString("\n")

    fun report(context: Context): String {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val configuration = context.resources.configuration
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        val enabled = accessibility.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        val selected = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val ownIme = ComponentName.unflattenFromString(selected.orEmpty())?.packageName == context.packageName
        val hardwareVisibility = Settings.Secure.getString(context.contentResolver, "show_ime_with_hard_keyboard")
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(EVENTS, "").orEmpty()
        val events = synchronized(this) { completeHistory(cachedHistory ?: saved) }
        return buildString {
            appendLine("OpenVoiceIME $version shortcut diagnostics")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Shortcut enabled=$enabled; voice IME selected=$ownIme; hardware IME visibility=$hardwareVisibility")
            appendLine("Orientation=${configuration.orientation}; keyboard=${configuration.keyboard}; hardKeyboardHidden=${configuration.hardKeyboardHidden}")
            appendLine("Event times are milliseconds since device boot. No typed text, audio, tokens or server addresses.")
            append(events.ifEmpty { "No shortcut events recorded. Try the shortcut, then copy this report again." })
        }
    }
}
