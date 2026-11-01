package dev.rankis.openime.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import dev.rankis.openime.R

class KeyboardSetupGuide(
    private val context: Context,
    private val root: View,
    private val preferences: SharedPreferences,
    private val launch: (Intent) -> Unit,
    private val showPicker: () -> Unit,
    configureServer: () -> Unit,
    private val heliSettings: () -> Intent? = { findHeliboardSettings(context) },
) {
    companion object {
        const val PREFERENCES = "openime_keyboard_setup"
        private const val ROUTE = "route"
        const val HELIBOARD_DOWNLOAD = "https://github.com/HeliBorg/HeliBoard/releases/latest"

        private fun findHeliboardSettings(context: Context): Intent? {
            val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val ime = manager.inputMethodList.firstOrNull { it.packageName == "helium314.keyboard" }
                ?: return null
            val settings = ime.settingsActivity?.takeIf { it.isNotBlank() } ?: return null
            val className = if (settings.startsWith(".")) ime.packageName + settings else settings
            return Intent(Intent.ACTION_MAIN).setComponent(ComponentName(ime.packageName, className))
        }
    }

    private val panel = root.findViewById<View>(R.id.setupGuidePanel)
    private val routes = root.findViewById<RadioGroup>(R.id.setupRouteGroup)
    private val actionStatus = root.findViewById<TextView>(R.id.setupActionStatus)
    private var initialExpansionSet = false

    init {
        when (preferences.getString(ROUTE, null)) {
            "heliboard" -> routes.check(R.id.setupUseHeliboard)
            "current" -> routes.check(R.id.setupKeepKeyboard)
        }
        renderRoute()
        routes.setOnCheckedChangeListener { _, checkedId ->
            val route = when (checkedId) {
                R.id.setupUseHeliboard -> "heliboard"
                R.id.setupKeepKeyboard -> "current"
                else -> return@setOnCheckedChangeListener
            }
            preferences.edit().putString(ROUTE, route).apply()
            renderRoute()
        }
        root.findViewById<Button>(R.id.setupGuideToggle).setOnClickListener {
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        root.findViewById<Button>(R.id.enableImeButton).setOnClickListener {
            open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        root.findViewById<Button>(R.id.setupDownloadHeliboard).setOnClickListener {
            open(Intent(Intent.ACTION_VIEW, Uri.parse(HELIBOARD_DOWNLOAD)))
        }
        root.findViewById<Button>(R.id.setupOpenHeliboard).setOnClickListener {
            val intent = heliSettings()
            if (intent == null) showError(R.string.setup_heliboard_missing) else open(intent)
        }
        root.findViewById<Button>(R.id.setupChooseKeyboard).setOnClickListener {
            try {
                actionStatus.visibility = View.GONE
                showPicker()
            } catch (_: SecurityException) {
                showError(R.string.setup_action_failed)
            }
        }
        root.findViewById<Button>(R.id.setupConfigureServer).setOnClickListener {
            panel.visibility = View.GONE
            configureServer()
        }
    }

    fun refresh(microphoneGranted: Boolean, imeEnabled: Boolean) {
        if (!initialExpansionSet) {
            panel.visibility = if (!microphoneGranted || !imeEnabled || routes.checkedRadioButtonId == -1) {
                View.VISIBLE
            } else View.GONE
            initialExpansionSet = true
        }
        val available = heliSettings() != null
        root.findViewById<Button>(R.id.setupOpenHeliboard).isEnabled = available
        root.findViewById<TextView>(R.id.setupHeliboardStatus).setText(
            if (available) R.string.setup_heliboard_available else R.string.setup_heliboard_missing,
        )
    }

    private fun renderRoute() {
        root.findViewById<View>(R.id.setupHeliboardPanel).visibility =
            if (routes.checkedRadioButtonId == R.id.setupUseHeliboard) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.setupCurrentKeyboardPanel).visibility =
            if (routes.checkedRadioButtonId == R.id.setupKeepKeyboard) View.VISIBLE else View.GONE
    }

    private fun open(intent: Intent) {
        try {
            actionStatus.visibility = View.GONE
            launch(intent)
        } catch (_: ActivityNotFoundException) {
            showError(R.string.setup_action_failed)
        } catch (_: SecurityException) {
            showError(R.string.setup_action_failed)
        }
    }

    private fun showError(message: Int) {
        actionStatus.setText(message)
        actionStatus.visibility = View.VISIBLE
    }
}
