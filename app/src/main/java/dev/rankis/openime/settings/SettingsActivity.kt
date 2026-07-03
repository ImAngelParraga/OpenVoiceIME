package dev.rankis.openime.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import dev.rankis.openime.R
import dev.rankis.openime.metrics.TranscriptionMetricsStore
import dev.rankis.openime.metrics.formatTranscriptionMetrics
import dev.rankis.openime.stt.DiagnosticResult
import dev.rankis.openime.stt.NetworkDiagnostics
import dev.rankis.openime.stt.TranscriptionError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private companion object {
        const val MAX_LANGUAGE_SEARCH_RESULTS = 6
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val diagnostics = NetworkDiagnostics()

    private lateinit var store: SettingsStore
    private lateinit var metricsStore: TranscriptionMetricsStore
    private lateinit var providerPresetSpinner: Spinner
    private lateinit var baseUrlInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var customModelInput: EditText
    private lateinit var presetNameInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var appLanguageSpinner: Spinner
    private lateinit var languageSearchInput: EditText
    private lateinit var languageSearchResultsContainer: LinearLayout
    private lateinit var favoriteLanguagesToggleButton: Button
    private lateinit var favoriteLanguagesContainer: LinearLayout
    private lateinit var diagnosticPanel: ScrollView
    private lateinit var diagnosticOutput: TextView
    private lateinit var serverStatusIcon: TextView
    private lateinit var serverStatusText: TextView
    private lateinit var microphoneStatusIcon: TextView
    private lateinit var microphoneStatusText: TextView
    private lateinit var keyboardStatusIcon: TextView
    private lateinit var keyboardStatusText: TextView
    private lateinit var metricsOutput: TextView
    private lateinit var trailingSpaceCheck: CheckBox
    private lateinit var hideAfterSuccessCheck: CheckBox
    private lateinit var confirmBeforeInsertCheck: CheckBox
    private lateinit var selectInsertedTextCheck: CheckBox
    private var suppressPresetChanges = false
    private var suppressModelChanges = false
    private var suppressAutosave = false
    private var selectedLanguageCode: String? = null
    private var favoriteLanguageCodes: List<String?> = emptyList()
    private var favoriteLanguagesExpanded = false
    private var lastSavedConnectionFingerprint: String? = null
    private var presetOptions: List<ProviderPresetOption> = emptyList()
    private var modelChoices: List<String> = emptyList()
    private var languageOptions: List<TranscriptionLanguageOption> = emptyList()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshSetupStatus()
        val messageRes = if (granted) R.string.toast_microphone_granted else R.string.toast_microphone_denied
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = SettingsStore(this)
        metricsStore = TranscriptionMetricsStore(this)
        bindViews()
        runWithoutAutosave {
            setupProviderPresetSpinner()
            setupModelSpinner()
            setupAppLanguageSpinner()
            setupLanguageSearch()
            loadSettings()
        }
        refreshMetrics()
        setupButtons()
        setupAutosave()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::metricsOutput.isInitialized) {
            refreshMetrics()
            refreshSetupStatus()
        }
    }

    private fun bindViews() {
        providerPresetSpinner = findViewById(R.id.providerPresetSpinner)
        baseUrlInput = findViewById(R.id.baseUrlInput)
        modelSpinner = findViewById(R.id.modelSpinner)
        customModelInput = findViewById(R.id.customModelInput)
        presetNameInput = findViewById(R.id.presetNameInput)
        tokenInput = findViewById(R.id.tokenInput)
        appLanguageSpinner = findViewById(R.id.appLanguageSpinner)
        languageSearchInput = findViewById(R.id.languageSearchInput)
        languageSearchResultsContainer = findViewById(R.id.languageSearchResultsContainer)
        favoriteLanguagesToggleButton = findViewById(R.id.favoriteLanguagesToggleButton)
        favoriteLanguagesContainer = findViewById(R.id.favoriteLanguagesContainer)
        diagnosticPanel = findViewById(R.id.diagnosticPanel)
        diagnosticOutput = findViewById(R.id.diagnosticOutput)
        serverStatusIcon = findViewById(R.id.serverStatusIcon)
        serverStatusText = findViewById(R.id.serverStatusText)
        microphoneStatusIcon = findViewById(R.id.microphoneStatusIcon)
        microphoneStatusText = findViewById(R.id.microphoneStatusText)
        keyboardStatusIcon = findViewById(R.id.keyboardStatusIcon)
        keyboardStatusText = findViewById(R.id.keyboardStatusText)
        metricsOutput = findViewById(R.id.metricsOutput)
        trailingSpaceCheck = findViewById(R.id.trailingSpaceCheck)
        hideAfterSuccessCheck = findViewById(R.id.hideAfterSuccessCheck)
        confirmBeforeInsertCheck = findViewById(R.id.confirmBeforeInsertCheck)
        selectInsertedTextCheck = findViewById(R.id.selectInsertedTextCheck)
        findViewById<TextView>(R.id.versionText).text = appVersionLabel()
        showServerStatusIdle()
    }

    private fun setupProviderPresetSpinner() {
        providerPresetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressPresetChanges) {
                    return
                }
                val preset = presetOptions.getOrNull(position) ?: return
                runWithoutAutosave {
                    applyProviderPreset(preset)
                }
                saveSettingsSilently()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupModelSpinner() {
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressModelChanges) {
                    return
                }
                customModelInput.visibility = if (modelChoices.getOrNull(position) == CUSTOM_MODEL_LABEL) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                saveSettingsSilently()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupLanguageSearch() {
        languageOptions = transcriptionLanguageOptions(Locale.getDefault())
        languageSearchInput.doAfterTextChanged {
            refreshLanguageSearchResults(it?.toString().orEmpty())
        }
        languageSearchInput.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(view)
                true
            } else {
                false
            }
        }
    }

    private fun setupAppLanguageSpinner() {
        val labels = resources.getStringArray(R.array.app_language_choice_labels).toList()
        appLanguageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        appLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressAutosave) {
                    return
                }
                val previous = store.load().appLanguageChoice
                saveSettingsSilently()
                val selected = AppLanguageChoice.entries.getOrNull(position) ?: AppLanguageChoice.SYSTEM
                if (selected != previous) {
                    recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun loadSettings() {
        val settings = store.load()
        runWithoutAutosave {
            refreshPresetSpinner(settings.selectedPresetId)
            baseUrlInput.setText(settings.baseUrl)
            refreshModelSpinner(settings.model)
            tokenInput.setText(settings.apiToken)
            appLanguageSpinner.setSelection(settings.appLanguageChoice.ordinal)
            selectedLanguageCode = settings.languageCode
            favoriteLanguageCodes = settings.favoriteTranscriptionLanguageCodes
            languageSearchInput.setText("")
            refreshLanguageSearchResults()
            refreshFavoriteLanguages()
            trailingSpaceCheck.isChecked = settings.appendTrailingSpace
            hideAfterSuccessCheck.isChecked = settings.hideAfterSuccess
            confirmBeforeInsertCheck.isChecked = settings.confirmBeforeInsert
            selectInsertedTextCheck.isChecked = settings.selectInsertedText
            lastSavedConnectionFingerprint = connectionTestFingerprint(settings)
            showConnectionTestStatus(settings)
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.permissionButton).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.toast_microphone_already_granted, Toast.LENGTH_SHORT).show()
            } else {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        findViewById<Button>(R.id.enableImeButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.testServerButton).setOnClickListener {
            testServerConnection()
        }

        findViewById<Button>(R.id.resetMetricsButton).setOnClickListener {
            metricsStore.clear()
            refreshMetrics()
            Toast.makeText(this, R.string.toast_metrics_reset, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.savePresetButton).setOnClickListener {
            saveCurrentServerAsPreset()
        }

        favoriteLanguagesToggleButton.setOnClickListener {
            favoriteLanguagesExpanded = !favoriteLanguagesExpanded
            refreshFavoriteLanguages()
        }

        findViewById<Button>(R.id.copyMetricsButton).setOnClickListener {
            copyMetrics()
        }

        findViewById<Button>(R.id.shareMetricsButton).setOnClickListener {
            shareMetrics()
        }
    }

    private fun setupAutosave() {
        baseUrlInput.doAfterTextChanged { saveSettingsSilently() }
        customModelInput.doAfterTextChanged { saveSettingsSilently() }
        tokenInput.doAfterTextChanged { saveSettingsSilently() }

        trailingSpaceCheck.setOnCheckedChangeListener { _, _ -> saveSettingsSilently() }
        hideAfterSuccessCheck.setOnCheckedChangeListener { _, _ -> saveSettingsSilently() }
        confirmBeforeInsertCheck.setOnCheckedChangeListener { _, _ -> saveSettingsSilently() }
        selectInsertedTextCheck.setOnCheckedChangeListener { _, _ -> saveSettingsSilently() }
        refreshSetupStatus()
    }

    private fun refreshPresetSpinner(selectedPresetId: String) {
        presetOptions = providerOptions(store.loadCustomPresets())
        val labels = presetOptions.map { it.displayName }
        providerPresetSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val selectedIndex = presetOptions.indexOfFirst { it.id == selectedPresetId }.takeIf { it >= 0 } ?: 0
        suppressPresetChanges = true
        providerPresetSpinner.setSelection(selectedIndex)
        suppressPresetChanges = false
    }

    private fun applyProviderPreset(preset: ProviderPresetOption) {
        if (preset.id == BuiltInProviderPreset.CUSTOM.id) {
            refreshModelSpinner(currentModelValue())
            return
        }
        baseUrlInput.setText(preset.baseUrl)
        if (preset.isSaved) {
            tokenInput.setText(preset.apiToken)
        }
        refreshModelSpinner(preset.model)
    }

    private fun refreshModelSpinner(model: String) {
        val preset = presetOptions.getOrNull(providerPresetSpinner.selectedItemPosition)
            ?: builtInProviderOptions().first()
        modelChoices = modelChoicesFor(preset, model)
        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelChoices)
        val selectedIndex = modelChoices.indexOf(model.trim()).takeIf { it >= 0 }
            ?: modelChoices.indexOf(CUSTOM_MODEL_LABEL)
        suppressModelChanges = true
        modelSpinner.setSelection(selectedIndex.coerceAtLeast(0))
        suppressModelChanges = false
        customModelInput.setText(model)
        customModelInput.visibility = if (modelChoices.getOrNull(selectedIndex) == CUSTOM_MODEL_LABEL) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun currentPresetId(): String {
        return presetOptions.getOrNull(providerPresetSpinner.selectedItemPosition)?.id
            ?: BuiltInProviderPreset.OPENAI.id
    }

    private fun currentModelValue(): String {
        val selected = modelChoices.getOrNull(modelSpinner.selectedItemPosition)
        return if (selected == CUSTOM_MODEL_LABEL || selected.isNullOrBlank()) {
            customModelInput.text.toString()
        } else {
            selected
        }
    }

    private fun refreshLanguageSearchResults(query: String = languageSearchInput.text?.toString().orEmpty()) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            renderLanguageSearchResults(emptyList(), showEmptyState = false)
            return
        }
        val results = languageOptions.filter { option ->
            option.label.contains(normalizedQuery, ignoreCase = true) ||
                option.code?.contains(normalizedQuery, ignoreCase = true) == true
        }.take(MAX_LANGUAGE_SEARCH_RESULTS)
        renderLanguageSearchResults(results, showEmptyState = true)
    }

    private fun renderLanguageSearchResults(results: List<TranscriptionLanguageOption>, showEmptyState: Boolean) {
        languageSearchResultsContainer.removeAllViews()
        if (results.isEmpty()) {
            languageSearchResultsContainer.visibility = if (showEmptyState) View.VISIBLE else View.GONE
            if (!showEmptyState) {
                return
            }
            languageSearchResultsContainer.addView(
                TextView(this).apply {
                    text = getString(R.string.language_no_matches)
                    setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.openime_muted))
                    textSize = 14f
                    setPadding(0, 8, 0, 8)
                },
            )
            return
        }
        languageSearchResultsContainer.visibility = View.VISIBLE
        results.forEach { option ->
            languageSearchResultsContainer.addView(
                Button(this).apply {
                    text = option.label
                    setOnClickListener { addFavoriteLanguage(option.code) }
                },
            )
        }
    }

    private fun addFavoriteLanguage(languageCode: String?) {
        if (languageCode in favoriteLanguageCodes) {
            Toast.makeText(this, R.string.toast_language_favorite_exists, Toast.LENGTH_SHORT).show()
            return
        }
        if (favoriteLanguageCodes.size >= MAX_FAVORITE_TRANSCRIPTION_LANGUAGES) {
            Toast.makeText(this, R.string.toast_language_favorite_limit, Toast.LENGTH_SHORT).show()
            return
        }
        favoriteLanguageCodes = normalizeFavoriteTranscriptionLanguageCodes(favoriteLanguageCodes + languageCode)
        refreshFavoriteLanguages()
        languageSearchInput.setText("")
        refreshLanguageSearchResults()
        hideKeyboard(languageSearchInput)
        saveSettingsSilently()
        Toast.makeText(this, R.string.toast_language_favorite_added, Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun removeFavoriteLanguage(languageCode: String?) {
        if (languageCode == null) {
            return
        }
        favoriteLanguageCodes = normalizeFavoriteTranscriptionLanguageCodes(favoriteLanguageCodes.filterNot { it == languageCode })
        refreshFavoriteLanguages()
        saveSettingsSilently()
    }

    private fun refreshFavoriteLanguages() {
        favoriteLanguagesContainer.removeAllViews()
        favoriteLanguageCodes = normalizeFavoriteTranscriptionLanguageCodes(favoriteLanguageCodes)
        favoriteLanguagesToggleButton.text = if (favoriteLanguagesExpanded) {
            getString(R.string.settings_quick_languages_hide)
        } else {
            getString(R.string.settings_quick_languages_format, favoriteLanguageSummary())
        }
        favoriteLanguagesContainer.visibility = if (favoriteLanguagesExpanded) View.VISIBLE else View.GONE
        favoriteLanguageCodes.forEach { code ->
            val button = Button(this).apply {
                text = if (code == null) {
                    getString(R.string.language_auto).uppercase(Locale.ROOT)
                } else {
                    "${languageLabel(code)}  X"
                }
                isEnabled = code != null
                setOnClickListener { removeFavoriteLanguage(code) }
            }
            favoriteLanguagesContainer.addView(button)
        }
    }

    private fun languageLabel(languageCode: String): String {
        val label = languageOptions.firstOrNull { it.code == languageCode }?.label
        return label ?: languageCode.uppercase(Locale.ROOT)
    }

    private fun favoriteLanguageSummary(): String {
        return favoriteLanguageCodes.joinToString(", ") { code ->
            code?.uppercase(Locale.ROOT) ?: getString(R.string.language_auto).uppercase(Locale.ROOT)
        }
    }

    private fun saveCurrentServerAsPreset() {
        val name = presetNameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, R.string.toast_preset_name_required, Toast.LENGTH_LONG).show()
            return
        }
        val baseUrl = baseUrlInput.text.toString().trim().trimEnd('/')
        validateServerUrl(baseUrl).let { validation ->
            if (!validation.isValid) {
                Toast.makeText(this, localizedValidationMessage(validation), Toast.LENGTH_LONG).show()
                return
            }
        }
        val model = currentModelValue().trim()
        if (model.isBlank()) {
            Toast.makeText(this, R.string.toast_model_required, Toast.LENGTH_LONG).show()
            return
        }

        val token = tokenInput.text.toString()
        val preset = store.saveCustomPreset(name, baseUrl, model, token)
        runWithoutAutosave {
            refreshPresetSpinner(preset.id)
            baseUrlInput.setText(preset.baseUrl)
            tokenInput.setText(preset.apiToken)
            refreshModelSpinner(preset.model)
            presetNameInput.setText("")
        }
        saveSettingsSilently()
        Toast.makeText(this, R.string.toast_preset_saved, Toast.LENGTH_SHORT).show()
    }

    private fun currentFormSettings(): AppSettings {
        return AppSettings(
            selectedPresetId = currentPresetId(),
            baseUrl = baseUrlInput.text.toString(),
            model = currentModelValue(),
            apiToken = tokenInput.text.toString(),
            appLanguageChoice = AppLanguageChoice.entries.getOrNull(appLanguageSpinner.selectedItemPosition)
                ?: AppLanguageChoice.SYSTEM,
            transcriptionLanguageCode = selectedLanguageCode,
            favoriteTranscriptionLanguageCodes = favoriteLanguageCodes,
            appendTrailingSpace = trailingSpaceCheck.isChecked,
            hideAfterSuccess = hideAfterSuccessCheck.isChecked,
            confirmBeforeInsert = confirmBeforeInsertCheck.isChecked,
            selectInsertedText = selectInsertedTextCheck.isChecked,
        )
    }

    private fun testServerConnection() {
        val settings = currentFormSettings()
        val validation = validateSettings(settings)
        if (!validation.isValid) {
            val message = localizedValidationMessage(validation)
            showServerStatusFailure(message)
            showDiagnosticDetails(message)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, R.string.toast_testing_server, Toast.LENGTH_SHORT).show()
        hideDiagnosticDetails()
        showServerStatusTesting()
        scope.launch {
            when (val result = diagnostics.test(settings)) {
                is DiagnosticResult.Success -> {
                    val message = getString(R.string.diagnostic_endpoint_reachable)
                    store.markConnectionTestSucceeded(settings)
                    hideDiagnosticDetails()
                    showServerStatusSuccess(message)
                    Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
                }
                is DiagnosticResult.Failure -> {
                    val message = localizedTranscriptionError(result.error)
                    showServerStatusFailure(message)
                    showDiagnosticDetails(message)
                    Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun appVersionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        return getString(R.string.version_format, info.versionName)
    }

    private fun refreshMetrics() {
        metricsOutput.setText(formatTranscriptionMetrics(metricsStore.load()))
    }

    private fun saveSettingsSilently() {
        if (suppressAutosave) {
            return
        }
        val settings = currentFormSettings()
        store.save(settings)
        val fingerprint = connectionTestFingerprint(settings)
        if (lastSavedConnectionFingerprint != fingerprint) {
            lastSavedConnectionFingerprint = fingerprint
            showConnectionTestStatus(settings)
        }
    }

    private fun runWithoutAutosave(block: () -> Unit) {
        suppressAutosave = true
        try {
            block()
        } finally {
            suppressAutosave = false
        }
    }

    private fun currentMetricsText(): String {
        return metricsOutput.text?.toString().orEmpty()
    }

    private fun copyMetrics() {
        val text = currentMetricsText()
        if (text.isBlank()) {
            Toast.makeText(this, R.string.toast_no_metrics_copy, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.metrics_clip_label), text))
        Toast.makeText(this, R.string.toast_metrics_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareMetrics() {
        val text = currentMetricsText()
        if (text.isBlank()) {
            Toast.makeText(this, R.string.toast_no_metrics_share, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.metrics_clip_label))
            .putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(intent, getString(R.string.metrics_share_chooser)))
    }

    private fun showDiagnosticDetails(message: String) {
        diagnosticOutput.text = message
        diagnosticPanel.visibility = View.VISIBLE
    }

    private fun hideDiagnosticDetails() {
        diagnosticOutput.text = ""
        diagnosticPanel.visibility = View.GONE
    }

    private fun showServerStatusIdle() {
        setServerStatus(
            icon = "•",
            text = getString(R.string.status_not_tested),
            colorRes = R.color.openime_muted,
        )
    }

    private fun showServerStatusTesting() {
        setServerStatus(
            icon = "•",
            text = getString(R.string.status_testing),
            colorRes = R.color.openime_muted,
        )
    }

    private fun showServerStatusSuccess(@Suppress("UNUSED_PARAMETER") message: String) {
        setServerStatus(
            icon = "✓",
            text = getString(R.string.status_server_reachable),
            colorRes = R.color.openime_accent,
        )
    }

    private fun showServerStatusFailure(message: String) {
        setServerStatus(
            icon = "!",
            text = shortStatusMessage(message),
            colorRes = R.color.openime_danger,
        )
    }

    private fun showConnectionTestStatus(settings: AppSettings) {
        if (store.hasCurrentConnectionTest(settings)) {
            showServerStatusSuccess(getString(R.string.diagnostic_endpoint_reachable))
        } else {
            showServerStatusIdle()
        }
    }

    private fun setServerStatus(icon: String, text: String, colorRes: Int) {
        val color = ContextCompat.getColor(this, colorRes)
        serverStatusIcon.text = icon
        serverStatusIcon.setTextColor(color)
        serverStatusText.text = text
        serverStatusText.setTextColor(color)
    }

    private fun refreshSetupStatus() {
        showMicrophoneStatus(
            granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
        showKeyboardStatus(enabled = isOpenImeEnabled())
    }

    private fun showMicrophoneStatus(granted: Boolean) {
        setSetupStatus(
            iconView = microphoneStatusIcon,
            textView = microphoneStatusText,
            icon = if (granted) "✓" else "!",
            text = getString(if (granted) R.string.toast_microphone_granted else R.string.status_microphone_permission_required),
            colorRes = if (granted) R.color.openime_accent else R.color.openime_danger,
        )
    }

    private fun showKeyboardStatus(enabled: Boolean) {
        setSetupStatus(
            iconView = keyboardStatusIcon,
            textView = keyboardStatusText,
            icon = if (enabled) "✓" else "!",
            text = getString(if (enabled) R.string.status_keyboard_enabled else R.string.status_keyboard_required),
            colorRes = if (enabled) R.color.openime_accent else R.color.openime_danger,
        )
    }

    private fun setSetupStatus(
        iconView: TextView,
        textView: TextView,
        icon: String,
        text: String,
        colorRes: Int,
    ) {
        val color = ContextCompat.getColor(this, colorRes)
        iconView.text = icon
        iconView.setTextColor(color)
        textView.text = text
        textView.setTextColor(color)
    }

    private fun isOpenImeEnabled(): Boolean {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return inputMethodManager.enabledInputMethodList.any { inputMethodInfo ->
            inputMethodInfo.packageName == packageName
        }
    }

    private fun shortStatusMessage(message: String): String {
        val compact = message.replace(Regex("\\s+"), " ").trim()
        if (compact.isBlank()) {
            return getString(R.string.status_server_test_failed)
        }
        val httpMatch = Regex("HTTP\\s+\\d+").find(compact)
        if (httpMatch != null) {
            return httpMatch.value
        }
        return compact.take(56)
    }

    private fun localizedValidationMessage(validation: SettingsValidation): String {
        return when (validation.error) {
            SettingsValidationError.ApiTokenRequired -> getString(R.string.validation_api_token_required)
            SettingsValidationError.ModelRequired -> getString(R.string.validation_model_required)
            SettingsValidationError.ServerUrlRequired -> getString(R.string.validation_server_url)
            null -> validation.message ?: getString(R.string.validation_invalid_settings)
        }
    }

    private fun localizedTranscriptionError(error: TranscriptionError): String {
        return when (error) {
            TranscriptionError.Timeout -> getString(R.string.transcription_timeout)
            TranscriptionError.Unauthorized -> getString(R.string.transcription_unauthorized)
            TranscriptionError.ServerBusy -> getString(R.string.transcription_server_busy)
            TranscriptionError.EmptyTranscript -> getString(R.string.transcription_empty)
            is TranscriptionError.UnknownHost -> getString(R.string.transcription_unknown_host)
            is TranscriptionError.ConnectionRefused -> getString(R.string.transcription_connection_refused)
            is TranscriptionError.Http -> error.detail?.let {
                getString(R.string.transcription_http_detail_format, error.code, it)
            } ?: getString(R.string.transcription_http_format, error.code)
            is TranscriptionError.Network -> getString(R.string.transcription_network_format, error.detail)
            is TranscriptionError.Parse -> getString(R.string.transcription_parse)
        }
    }
}
