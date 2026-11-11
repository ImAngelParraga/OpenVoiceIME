package dev.rankis.openime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Local event history containing fixed labels and lifecycle flags, never editor or connection data. */
internal object ShortcutDiagnostics {
    private const val PREFERENCES = "openime_shortcut_diagnostics"
    private const val EVENTS = "events"
    private const val MAX_EVENTS = 80

    @Synchronized
    fun record(context: Context, event: String) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val lines = preferences.getString(EVENTS, "").orEmpty().lines().filter { it.isNotEmpty() }
        val entry = "${SystemClock.elapsedRealtime()}ms ${event.take(240)}"
        preferences.edit().putString(EVENTS, (lines + entry).takeLast(MAX_EVENTS).joinToString("\n")).apply()
    }

    @Synchronized
    fun report(context: Context): String {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val configuration = context.resources.configuration
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        val enabled = accessibility.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        val selected = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val ownIme = ComponentName.unflattenFromString(selected.orEmpty())?.packageName == context.packageName
        val hardwareVisibility = Settings.Secure.getString(context.contentResolver, "show_ime_with_hard_keyboard")
        val events = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(EVENTS, "").orEmpty()
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
