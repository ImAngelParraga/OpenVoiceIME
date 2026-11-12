package dev.rankis.openime.settings

import android.os.Bundle
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import dev.rankis.openime.R
import dev.rankis.openime.stt.AsrModelCatalog
import dev.rankis.openime.stt.DiagnosticResult
import dev.rankis.openime.stt.NetworkDiagnostics
import dev.rankis.openime.stt.TranscriptionError
import dev.rankis.openime.stt.localizedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ServerSettingsActivity : SettingsScreenActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val diagnostics = NetworkDiagnostics()
    private val modelCatalog = AsrModelCatalog()

    private lateinit var store: SettingsStore
    private lateinit var presetRow: SettingsRow
    private lateinit var modelRow: SettingsRow
    private lateinit var connectionStatusRow: SettingsRow
    private lateinit var baseUrlInput: EditText
    private lateinit var baseUrlError: TextView
    private lateinit var tokenInput: EditText
    private lateinit var tokenVisibilityButton: Button
    private lateinit var customModelInput: EditText
    private lateinit var modelCatalogStatus: TextView
    private lateinit var diagnosticOutput: TextView
    private lateinit var testServerButton: Button

    private var presetOptions: List<ProviderPresetOption> = emptyList()
    private lateinit var selectedPreset: ProviderPresetOption
    private var model = ""
    private var customModel = false
    private var discoveredModels: List<String>? = null
    private var modelCatalogSource: Pair<String, String>? = null
    private var modelCatalogRequest = 0
    private var modelCatalogJob: Job? = null
    private var connectionTestJob: Job? = null
    private var connectionTestRequest = 0L
    private var lastSavedConnectionFingerprint: String? = null
    private var suppressAutosave = false
    private var tokenVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenContent(R.layout.activity_server_settings, R.string.screen_server)
        store = SettingsStore(this)
        bindViews()
        loadSettings()
        bindListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshModelCatalog()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        presetRow = SettingsRow.attach(findViewById(R.id.presetRow))
        modelRow = SettingsRow.attach(findViewById(R.id.modelRow))
        connectionStatusRow = SettingsRow.attach(findViewById(R.id.connectionStatusRow))
        baseUrlInput = findViewById(R.id.baseUrlInput)
        baseUrlError = findViewById(R.id.baseUrlError)
        tokenInput = findViewById(R.id.tokenInput)
        tokenVisibilityButton = findViewById(R.id.tokenVisibilityButton)
        customModelInput = findViewById(R.id.customModelInput)
        modelCatalogStatus = findViewById(R.id.modelCatalogStatus)
        diagnosticOutput = findViewById(R.id.diagnosticOutput)
        testServerButton = findViewById(R.id.testServerButton)
    }

    private fun loadSettings() {
        val settings = store.load()
        runWithoutAutosave {
            presetOptions = providerOptions(store.loadCustomPresets())
            selectedPreset = presetOptions.firstOrNull { it.id == settings.selectedPresetId } ?: presetOptions.first()
            baseUrlInput.setText(settings.baseUrl)
            tokenInput.setText(settings.apiToken)
            showModel(settings.model)
        }
        lastSavedConnectionFingerprint = connectionTestFingerprint(settings)
        renderPreset()
        renderUrlValidation()
        showStoredConnectionStatus(settings)
    }

    private fun bindListeners() {
        baseUrlInput.doAfterTextChanged {
            renderUrlValidation()
            onConnectionEdited()
        }
        tokenInput.doAfterTextChanged { onConnectionEdited() }
        customModelInput.doAfterTextChanged { text ->
            if (suppressAutosave) return@doAfterTextChanged
            model = text?.toString().orEmpty().trim()
            save()
        }
        tokenVisibilityButton.setOnClickListener { toggleTokenVisibility() }
        findViewById<Button>(R.id.refreshModelsButton).setOnClickListener { refreshModelCatalog() }
        testServerButton.setOnClickListener { testServerConnection() }
        findViewById<Button>(R.id.savePresetButton).setOnClickListener { askPresetName() }
    }

    private fun onConnectionEdited() {
        if (suppressAutosave) return
        save()
        refreshModelCatalog(CATALOG_DEBOUNCE_MS)
    }

    private fun renderPreset() {
        presetRow.bind(
            title = getString(R.string.settings_preset),
            summary = selectedPreset.displayName,
            showChevron = true,
            onClick = ::choosePreset,
        )
    }

    private fun choosePreset() {
        val current = presetOptions.indexOfFirst { it.id == selectedPreset.id }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_preset)
            .setSingleChoiceItems(presetOptions.map { it.displayName }.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                applyPreset(presetOptions[which])
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    @VisibleForTesting
    internal fun applyPreset(preset: ProviderPresetOption) {
        if (preset.id == selectedPreset.id) return
        selectedPreset = preset
        runWithoutAutosave {
            if (preset.id != BuiltInProviderPreset.CUSTOM.id) {
                baseUrlInput.setText(preset.baseUrl)
                tokenInput.setText(preset.apiToken)
            }
            showModel(if (preset.id == BuiltInProviderPreset.CUSTOM.id) model else preset.model)
        }
        renderPreset()
        save()
        refreshModelCatalog()
    }

    private fun listedModels(): List<String> =
        modelChoicesFor(selectedPreset, model, discoveredModels) - CUSTOM_MODEL_LABEL

    private fun showModel(value: String) {
        model = value.trim()
        customModel = model !in listedModels()
        runWithoutAutosave { customModelInput.setText(model) }
        renderModel()
    }

    private fun renderModel() {
        modelRow.bind(
            title = getString(R.string.settings_model),
            summary = if (customModel) getString(R.string.model_custom_summary) else model,
            showChevron = true,
            onClick = ::chooseModel,
        )
        customModelInput.visibility = if (customModel) View.VISIBLE else View.GONE
    }

    private fun chooseModel() {
        val models = listedModels()
        val labels = models + getString(R.string.model_custom_option)
        val current = if (customModel) models.size else models.indexOf(model)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_model)
            .setSingleChoiceItems(labels.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                if (which == models.size) useCustomModel() else useListedModel(models[which])
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun useCustomModel() {
        customModel = true
        runWithoutAutosave { customModelInput.setText(model) }
        renderModel()
        customModelInput.requestFocus()
        customModelInput.setSelection(customModelInput.length())
    }

    private fun useListedModel(value: String) {
        customModel = false
        model = value
        renderModel()
        save()
    }

    private fun renderUrlValidation() {
        val url = baseUrlInput.text.toString().trim()
        val invalid = url.isNotEmpty() && !validateServerUrl(url).isValid
        baseUrlError.visibility = if (invalid) View.VISIBLE else View.GONE
    }

    private fun toggleTokenVisibility() {
        tokenVisible = !tokenVisible
        tokenInput.transformationMethod = if (tokenVisible) null else PasswordTransformationMethod.getInstance()
        tokenInput.setSelection(tokenInput.length())
        tokenVisibilityButton.setText(if (tokenVisible) R.string.button_hide else R.string.button_show)
    }

    private fun refreshModelCatalog(delayMs: Long = 0) {
        val baseUrl = baseUrlInput.text.toString().trim().trimEnd('/')
        val token = tokenInput.text.toString()
        val source = baseUrl to token
        val request = ++modelCatalogRequest
        modelCatalogJob?.cancel()
        if (source != modelCatalogSource) {
            modelCatalogSource = source
            discoveredModels = null
            refreshModelMode()
        }
        if (!validateServerUrl(baseUrl).isValid || token.isBlank()) {
            modelCatalogStatus.setText(R.string.model_catalog_needs_connection)
            return
        }
        modelCatalogStatus.setText(R.string.model_catalog_loading)
        modelCatalogJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            val result = modelCatalog.fetch(baseUrl, token)
            if (request != modelCatalogRequest || source != currentCatalogSource()) return@launch
            result.onSuccess { models ->
                discoveredModels = models
                modelCatalogStatus.text = getString(R.string.model_catalog_available, models.size)
            }.onFailure {
                discoveredModels = null
                modelCatalogStatus.setText(R.string.model_catalog_unavailable)
            }
            refreshModelMode()
        }
    }

    private fun currentCatalogSource(): Pair<String, String> =
        baseUrlInput.text.toString().trim().trimEnd('/') to tokenInput.text.toString()

    private fun refreshModelMode() {
        if (!customModelInput.hasFocus()) customModel = model !in listedModels()
        renderModel()
    }

    private fun formSettings(): AppSettings = store.load().copy(
        selectedPresetId = selectedPreset.id,
        baseUrl = baseUrlInput.text.toString(),
        model = model,
        apiToken = tokenInput.text.toString(),
    )

    private fun save() {
        if (suppressAutosave) return
        val settings = formSettings()
        store.save(settings)
        val fingerprint = connectionTestFingerprint(settings)
        if (fingerprint != lastSavedConnectionFingerprint) {
            lastSavedConnectionFingerprint = fingerprint
            cancelConnectionTest()
            showStoredConnectionStatus(settings)
        }
    }

    private fun runWithoutAutosave(block: () -> Unit) {
        val previous = suppressAutosave
        suppressAutosave = true
        try {
            block()
        } finally {
            suppressAutosave = previous
        }
    }

    private fun testServerConnection() {
        cancelConnectionTest()
        val request = connectionTestRequest
        val settings = formSettings()
        val validation = validateSettings(settings)
        if (!validation.isValid) {
            showConnectionFailure(localizedValidationMessage(validation))
            return
        }
        showConnectionStatus(StatusTone.Pending, getString(R.string.status_testing), null)
        testServerButton.isEnabled = false
        connectionTestJob = scope.launch {
            val result = diagnostics.test(settings)
            if (request != connectionTestRequest) return@launch
            testServerButton.isEnabled = true
            when (result) {
                is DiagnosticResult.Success -> {
                    store.markConnectionTestSucceeded(settings)
                    showStoredConnectionStatus(settings)
                }
                is DiagnosticResult.Failure -> showConnectionFailure(testErrorMessage(result.error))
            }
        }
    }

    private fun cancelConnectionTest() {
        connectionTestRequest += 1
        connectionTestJob?.cancel()
        connectionTestJob = null
        testServerButton.isEnabled = true
    }

    private fun showStoredConnectionStatus(settings: AppSettings) {
        if (store.hasCurrentConnectionTest(settings)) {
            showConnectionStatus(
                StatusTone.Ok,
                getString(R.string.status_server_reachable),
                getString(R.string.server_tested_summary),
            )
        } else {
            showConnectionStatus(
                StatusTone.Pending,
                getString(R.string.status_not_tested),
                getString(R.string.server_not_tested_summary),
            )
        }
    }

    private fun showConnectionStatus(tone: StatusTone, title: String, summary: String?) {
        connectionStatusRow.bind(title = title, summary = summary, tone = tone)
        diagnosticOutput.visibility = View.GONE
    }

    private fun showConnectionFailure(message: String) {
        connectionStatusRow.bind(title = getString(R.string.status_server_test_failed), tone = StatusTone.Attention)
        diagnosticOutput.text = message
        diagnosticOutput.visibility = View.VISIBLE
    }

    private fun askPresetName() {
        val input = EditText(this).apply {
            setHint(R.string.hint_preset_name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = true
        }
        val padding = resources.getDimensionPixelSize(R.dimen.settings_dialog_padding)
        val container = FrameLayout(this).apply {
            setPaddingRelative(padding, 0, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_preset_title)
            .setMessage(R.string.save_preset_message)
            .setView(container)
            .setPositiveButton(R.string.button_save) { _, _ -> saveAsPreset(input.text.toString().trim()) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun saveAsPreset(name: String) {
        val baseUrl = baseUrlInput.text.toString().trim().trimEnd('/')
        val problem = when {
            name.isBlank() -> getString(R.string.toast_preset_name_required)
            !validateServerUrl(baseUrl).isValid -> localizedValidationMessage(validateServerUrl(baseUrl))
            model.isBlank() -> getString(R.string.toast_model_required)
            else -> null
        }
        if (problem != null) {
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
            return
        }
        val preset = store.saveCustomPreset(name, baseUrl, model, tokenInput.text.toString())
        presetOptions = providerOptions(store.loadCustomPresets())
        selectedPreset = presetOptions.first { it.id == preset.id }
        renderPreset()
        save()
        Toast.makeText(this, R.string.toast_preset_saved, Toast.LENGTH_SHORT).show()
    }

    private fun testErrorMessage(error: TranscriptionError): String =
        if (error == TranscriptionError.Timeout) getString(R.string.transcription_timeout) else error.localizedMessage(this)

    private fun localizedValidationMessage(validation: SettingsValidation): String = when (validation.error) {
        SettingsValidationError.ApiTokenRequired -> getString(R.string.validation_api_token_required)
        SettingsValidationError.ModelRequired -> getString(R.string.validation_model_required)
        SettingsValidationError.ServerUrlRequired -> getString(R.string.validation_server_url)
        null -> validation.message ?: getString(R.string.validation_invalid_settings)
    }

    private companion object {
        const val CATALOG_DEBOUNCE_MS = 500L
    }
}
