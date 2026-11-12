package dev.rankis.openime.settings

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import dev.rankis.openime.R
import dev.rankis.openime.sharing.MessageTranscriptionService

class MessageSettingsActivity : SettingsScreenActivity() {
    private lateinit var statusRow: SettingsRow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenContent(R.layout.activity_message_settings, R.string.screen_messages)
        statusRow = SettingsRow.attach(findViewById(R.id.messageStatusRow))
        statusRow.view.setPaddingRelative(0, 0, 0, 0)
        findViewById<Button>(R.id.messageSetupButton).setOnClickListener { openServiceSettings() }
    }

    override fun onResume() {
        super.onResume()
        val enabled = SetupStatus.accessibilityServiceEnabled(this, MessageTranscriptionService::class.java)
        statusRow.bind(
            title = getString(if (enabled) R.string.message_setup_enabled else R.string.message_setup_disabled),
            summary = getString(
                if (enabled) R.string.message_setup_enabled_summary else R.string.message_setup_disabled_summary,
            ),
            tone = if (enabled) StatusTone.Ok else StatusTone.Neutral,
        )
    }

    private fun openServiceSettings() {
        val component = ComponentName(this, MessageTranscriptionService::class.java)
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        runCatching { startActivity(details) }.onFailure {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
