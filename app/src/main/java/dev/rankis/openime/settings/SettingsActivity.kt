package dev.rankis.openime.settings

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import dev.rankis.openime.R
import dev.rankis.openime.metrics.TranscriptionMetricsStore
import dev.rankis.openime.metrics.formatMillis
import dev.rankis.openime.sharing.MessageTranscriptionService
import java.util.Locale

class SettingsActivity : SettingsScreenActivity() {
    private lateinit var store: SettingsStore
    private lateinit var metricsStore: TranscriptionMetricsStore

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Toast.makeText(
            this,
            if (granted) R.string.toast_microphone_granted else R.string.toast_microphone_denied,
            Toast.LENGTH_SHORT,
        ).show()
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenContent(R.layout.activity_settings)
        store = SettingsStore(this)
        metricsStore = TranscriptionMetricsStore(this)
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        findViewById<TextView>(R.id.versionText).text = getString(R.string.version_format, version)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val settings = store.load()
        renderReadiness(settings)
        renderTranscriptionRows(settings)
        renderMoreRows(settings)
    }

    private fun renderReadiness(settings: AppSettings) {
        val microphone = SetupStatus.microphoneGranted(this)
        val keyboard = SetupStatus.keyboardEnabled(this)
        val server = serverReadiness(settings, store.hasCurrentConnectionTest(settings))
        val done = listOf(microphone, keyboard, server == ServerReadiness.Ready).count { it }
        val title = findViewById<TextView>(R.id.readinessTitle)
        val summary = findViewById<TextView>(R.id.readinessSummary)
        if (done == READINESS_CHECKS) {
            title.setText(R.string.readiness_ready)
            summary.setText(R.string.readiness_ready_summary)
        } else {
            title.setText(R.string.readiness_incomplete)
            summary.text = getString(R.string.readiness_progress_format, done, READINESS_CHECKS)
        }

        val rows = findViewById<LinearLayout>(R.id.readinessRows)
        rows.removeAllViews()
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.check_microphone),
            summary = getString(if (microphone) R.string.check_microphone_ok else R.string.check_microphone_missing),
            tone = if (microphone) StatusTone.Ok else StatusTone.Attention,
            showChevron = !microphone,
            onClick = if (microphone) null else ({ requestMicPermission.launch(Manifest.permission.RECORD_AUDIO) }),
        )
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.check_keyboard),
            summary = getString(if (keyboard) R.string.check_keyboard_ok else R.string.check_keyboard_missing),
            tone = if (keyboard) StatusTone.Ok else StatusTone.Attention,
            showChevron = true,
            onClick = { open(SetupGuideActivity::class.java) },
        )
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.check_server),
            summary = getString(
                when (server) {
                    ServerReadiness.NotConfigured -> R.string.check_server_not_configured
                    ServerReadiness.NotTested -> R.string.check_server_not_tested
                    ServerReadiness.Ready -> R.string.check_server_ok
                },
            ),
            tone = when (server) {
                ServerReadiness.NotConfigured -> StatusTone.Attention
                ServerReadiness.NotTested -> StatusTone.Pending
                ServerReadiness.Ready -> StatusTone.Ok
            },
            showChevron = true,
            onClick = { open(ServerSettingsActivity::class.java) },
        )
    }

    private fun renderTranscriptionRows(settings: AppSettings) {
        val rows = findViewById<LinearLayout>(R.id.transcriptionRows)
        rows.removeAllViews()
        val preset = providerOptions(store.loadCustomPresets()).firstOrNull { it.id == settings.selectedPresetId }
        val connection = listOfNotNull(preset?.displayName, settings.normalizedModel().ifBlank { null })
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.screen_server),
            summary = connection.joinToString(" · "),
            showChevron = true,
            onClick = { open(ServerSettingsActivity::class.java) },
        )
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.screen_dictation),
            summary = dictationSummary(settings),
            showChevron = true,
            onClick = { open(DictationSettingsActivity::class.java) },
        )
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.screen_behavior),
            summary = getString(AfterInsertAction.of(settings).titleRes),
            showChevron = true,
            onClick = { open(BehaviorSettingsActivity::class.java) },
        )
    }

    private fun renderMoreRows(settings: AppSettings) {
        val rows = findViewById<LinearLayout>(R.id.moreRows)
        rows.removeAllViews()
        val messagesEnabled = SetupStatus.accessibilityServiceEnabled(this, MessageTranscriptionService::class.java)
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.screen_messages),
            summary = getString(if (messagesEnabled) R.string.status_on else R.string.status_off),
            showChevron = true,
            onClick = { open(MessageSettingsActivity::class.java) },
        )
        val metrics = metricsStore.load()
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.screen_metrics),
            summary = if (metrics.totalCount == 0L) {
                getString(R.string.metrics_empty)
            } else {
                getString(R.string.metrics_summary_format, metrics.totalCount, formatMillis(metrics.averageDurationMillis))
            },
            showChevron = true,
            onClick = { open(MetricsActivity::class.java) },
        )
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.screen_setup_guide),
            summary = getString(R.string.screen_setup_guide_summary),
            showChevron = true,
            onClick = { open(SetupGuideActivity::class.java) },
        )
        val languageLabels = resources.getStringArray(R.array.app_language_choice_labels)
        SettingsRow.addTo(rows).bind(
            title = getString(R.string.settings_app_language),
            summary = languageLabels[settings.appLanguageChoice.ordinal],
            onClick = { chooseAppLanguage(languageLabels, settings.appLanguageChoice) },
        )
    }

    private fun dictationSummary(settings: AppSettings): String {
        val languages = settings.favoriteTranscriptionLanguageCodes.joinToString(", ") { code ->
            code?.uppercase(Locale.ROOT) ?: getString(R.string.language_auto)
        }
        return if (settings.prompt == null) languages else getString(R.string.dictation_summary_with_prompt, languages)
    }

    private fun chooseAppLanguage(labels: Array<String>, current: AppLanguageChoice) {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_app_language)
            .setSingleChoiceItems(labels, current.ordinal) { dialog, which ->
                dialog.dismiss()
                val selected = AppLanguageChoice.entries[which]
                if (selected != current) {
                    store.update { it.copy(appLanguageChoice = selected) }
                    recreate()
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun open(screen: Class<*>) {
        startActivity(Intent(this, screen))
    }

    private companion object {
        const val READINESS_CHECKS = 3
    }
}
