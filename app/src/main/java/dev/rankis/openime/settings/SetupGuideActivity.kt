package dev.rankis.openime.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import dev.rankis.openime.R

class SetupGuideActivity : SettingsScreenActivity() {
    private lateinit var guide: KeyboardSetupGuide
    private lateinit var microphoneRow: SettingsRow
    private lateinit var keyboardRow: SettingsRow

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Toast.makeText(
            this,
            if (granted) R.string.toast_microphone_granted else R.string.toast_microphone_denied,
            Toast.LENGTH_SHORT,
        ).show()
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenContent(R.layout.activity_setup_guide, R.string.screen_setup_guide)
        microphoneRow = SettingsRow.attach(findViewById(R.id.microphoneStatusRow))
        keyboardRow = SettingsRow.attach(findViewById(R.id.keyboardStatusRow))
        findViewById<View>(R.id.permissionButton).setOnClickListener {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        guide = KeyboardSetupGuide(
            this,
            findViewById(android.R.id.content),
            getSharedPreferences(KeyboardSetupGuide.PREFERENCES, Context.MODE_PRIVATE),
            launch = { startActivity(it) },
            showPicker = ::showKeyboardPicker,
            configureServer = { startActivity(Intent(this, ServerSettingsActivity::class.java)) },
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val microphone = SetupStatus.microphoneGranted(this)
        microphoneRow.bind(
            title = getString(if (microphone) R.string.toast_microphone_granted else R.string.status_microphone_permission_required),
            tone = if (microphone) StatusTone.Ok else StatusTone.Attention,
        )
        findViewById<View>(R.id.permissionButton).visibility = if (microphone) View.GONE else View.VISIBLE
        val keyboard = SetupStatus.keyboardEnabled(this)
        keyboardRow.bind(
            title = getString(if (keyboard) R.string.status_keyboard_enabled else R.string.status_keyboard_required),
            tone = if (keyboard) StatusTone.Ok else StatusTone.Attention,
        )
        guide.refresh()
    }

    private fun showKeyboardPicker() {
        val input = findViewById<EditText>(R.id.setupTestInput)
        input.requestFocus()
        val manager = getSystemService(InputMethodManager::class.java)
        manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        manager.showInputMethodPicker()
    }
}
