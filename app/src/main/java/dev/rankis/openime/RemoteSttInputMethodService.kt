package dev.rankis.openime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import dev.rankis.openime.audio.AudioRecorder
import dev.rankis.openime.metrics.TranscriptionMetricsStore
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.settings.SettingsStore
import dev.rankis.openime.settings.SettingsValidation
import dev.rankis.openime.settings.SettingsValidationError
import dev.rankis.openime.settings.TranscriptionLanguageSettings
import dev.rankis.openime.settings.formatCommitText
import dev.rankis.openime.settings.validateSettings
import dev.rankis.openime.settings.withAppLocale
import dev.rankis.openime.stt.OpenAiCompatibleProvider
import dev.rankis.openime.stt.SttProvider
import dev.rankis.openime.stt.TranscriptionError
import dev.rankis.openime.stt.TranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class RemoteSttInputMethodService : android.inputmethodservice.InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val provider: SttProvider = OpenAiCompatibleProvider()

    private lateinit var recorder: AudioRecorder
    private lateinit var settingsStore: SettingsStore
    private lateinit var metricsStore: TranscriptionMetricsStore
    private lateinit var versionText: TextView
    private lateinit var statusText: TextView
    private lateinit var errorDetailsInput: EditText
    private lateinit var timerText: TextView
    private lateinit var levelBar: ProgressBar
    private lateinit var languageButton: TextView
    private lateinit var stopButton: Button
    private lateinit var cancelButton: Button
    private lateinit var retryButton: Button

    private var state: ImeState = ImeState.Idle
    private var startedAtMillis = 0L
    private var audioFile: File? = null
    private var pendingText: String? = null
    private var pendingHideAfterSuccess: Boolean = true
    private var pendingSelectInsertedText: Boolean = true
    private var lastErrorMessage: String? = null
    private var operationId: Long = 0L
    private var selectedLanguageCode: String? = null
    private var favoriteLanguageCodes: List<String?> = emptyList()

    private val tick = object : Runnable {
        override fun run() {
            if (state == ImeState.Recording) {
                updateRecordingFeedback()
                handler.postDelayed(this, 120)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate() {
        super.onCreate()
        recorder = AudioRecorder(this)
        settingsStore = SettingsStore(this)
        metricsStore = TranscriptionMetricsStore(this)
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.ime_voice_input, null)
        versionText = view.findViewById(R.id.versionText)
        statusText = view.findViewById(R.id.statusText)
        errorDetailsInput = view.findViewById(R.id.errorDetailsInput)
        timerText = view.findViewById(R.id.timerText)
        levelBar = view.findViewById(R.id.levelBar)
        languageButton = view.findViewById(R.id.languageButton)
        stopButton = view.findViewById(R.id.stopButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        retryButton = view.findViewById(R.id.retryButton)

        stopButton.setOnClickListener { onPrimaryAction() }
        cancelButton.setOnClickListener { cancelCurrentWork() }
        retryButton.setOnClickListener {
            if (state == ImeState.Error) {
                copyLastError()
            } else {
                retryUpload()
            }
        }

        versionText.text = appVersionLabel()
        setupLanguageControls()
        resetControls()
        startRecordingOrShowSetupError()
        return view
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::statusText.isInitialized && shouldStartFreshRecording()) {
            resetControls()
            startRecordingOrShowSetupError()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        handler.removeCallbacks(tick)
        if (state == ImeState.Recording) {
            recorder.cancel()
            audioFile = null
            state = ImeState.Idle
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        recorder.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startRecordingOrShowSetupError() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showError(getString(R.string.setup_grant_microphone))
            return
        }

        runCatching {
            handler.removeCallbacks(tick)
            operationId += 1
            audioFile = recorder.start()
            pendingText = null
            lastErrorMessage = null
            state = ImeState.Recording
            startedAtMillis = System.currentTimeMillis()
            applyDefaultStatusStyle()
            clearErrorDetails()
            statusText.setText(R.string.status_recording)
            timerText.text = "00:00"
            levelBar.progress = 0
            refreshLanguageControls(settingsStore.loadTranscriptionLanguage())
            languageButton.visibility = View.VISIBLE
            stopButton.setText(R.string.button_stop)
            stopButton.isEnabled = true
            cancelButton.setText(R.string.button_cancel)
            cancelButton.isEnabled = true
            retryButton.visibility = View.GONE
            handler.post(tick)
            validateRecordingSettingsAsync(operationId)
        }.onFailure {
            showError(getString(R.string.error_start_recorder))
        }
    }

    private fun onPrimaryAction() {
        when (state) {
            ImeState.Recording -> stopAndUpload()
            ImeState.ReadyToInsert -> commitPendingText()
            ImeState.Error -> retryUpload()
            else -> Unit
        }
    }

    private fun stopAndUpload() {
        handler.removeCallbacks(tick)
        val file = recorder.stop()
        if (file == null || !file.exists() || file.length() <= 0L) {
            showError(getString(R.string.error_recording_failed))
            return
        }
        audioFile = file
        upload(file)
    }

    private fun retryUpload() {
        val file = audioFile
        if (file == null || !file.exists()) {
            showError(getString(R.string.error_no_recording_retry))
            return
        }
        upload(file)
    }

    private fun upload(file: File) {
        val settings = settingsStore.load()
        val readinessError = connectionReadinessError(settings)
        if (readinessError != null) {
            showError(readinessError)
            return
        }
        val uploadOperationId = operationId
        state = ImeState.Uploading
        lastErrorMessage = null
        applyDefaultStatusStyle()
        clearErrorDetails()
        languageButton.visibility = View.GONE
        statusText.text = getString(R.string.status_uploading_format, (file.length() / 1024).coerceAtLeast(1))
        stopButton.isEnabled = false
        cancelButton.isEnabled = true
        retryButton.visibility = View.GONE

        scope.launch {
            Log.i(TAG, "Upload started for ${file.length()} bytes")
            val audioBytes = file.length()
            val startedAt = SystemClock.elapsedRealtime()
            val result = provider.transcribe(file, settings)
            val durationMillis = SystemClock.elapsedRealtime() - startedAt
            if (uploadOperationId != operationId) {
                return@launch
            }
            when (result) {
                is TranscriptionResult.Success -> handleTranscript(result.text, settings)
                is TranscriptionResult.Failure -> showError(localizedTranscriptionError(result.error), result.error)
            }
            recordTranscriptionMetrics(
                audioBytes = audioBytes,
                durationMillis = durationMillis,
                success = result is TranscriptionResult.Success,
            )
        }
    }

    private suspend fun handleTranscript(text: String, settings: AppSettings) = withContext(Dispatchers.Main.immediate) {
        val commitText = formatCommitText(text, settings.appendTrailingSpace)
        pendingHideAfterSuccess = settings.hideAfterSuccess
        pendingSelectInsertedText = settings.selectInsertedText
        if (settings.confirmBeforeInsert) {
            pendingText = commitText
            state = ImeState.ReadyToInsert
            lastErrorMessage = null
            applyDefaultStatusStyle()
            clearErrorDetails()
            statusText.text = text
            stopButton.setText(R.string.button_insert)
            stopButton.isEnabled = true
            cancelButton.setText(R.string.button_discard)
            cancelButton.isEnabled = true
            retryButton.visibility = View.GONE
        } else {
            pendingText = commitText
            commitPendingText()
        }
    }

    private fun validateRecordingSettingsAsync(validationOperationId: Long) {
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                connectionReadinessError(settingsStore.load())
            }
            if (validationOperationId != operationId || state != ImeState.Recording || error == null) {
                return@launch
            }
            handler.removeCallbacks(tick)
            recorder.cancel()
            audioFile = null
            showError(error)
        }
    }

    private fun connectionReadinessError(settings: AppSettings): String? {
        val validation = validateSettings(settings)
        if (!validation.isValid) {
            return localizedValidationMessage(validation)
        }
        if (!settingsStore.hasCurrentConnectionTest(settings)) {
            return getString(R.string.validation_connection_test_required)
        }
        return null
    }

    private fun recordTranscriptionMetrics(audioBytes: Long, durationMillis: Long, success: Boolean) {
        scope.launch(Dispatchers.IO) {
            metricsStore.record(
                audioBytes = audioBytes,
                durationMillis = durationMillis,
                success = success,
            )
        }
    }

    private fun commitPendingText() {
        val text = pendingText ?: return
        currentInputConnection.safeCommitText(text, pendingSelectInsertedText)
        audioFile?.delete()
        audioFile = null
        pendingText = null
        state = ImeState.Inserted
        lastErrorMessage = null
        applyDefaultStatusStyle()
        clearErrorDetails()
        statusText.setText(R.string.status_inserted)
        stopButton.isEnabled = false
        retryButton.visibility = View.GONE
        val switched = switchToNextKeyboard()
        if (!switched && pendingHideAfterSuccess) {
            requestHideSelf(0)
        }
    }

    private fun cancelCurrentWork() {
        operationId += 1
        handler.removeCallbacks(tick)
        if (state == ImeState.Recording) {
            recorder.cancel()
        }
        audioFile?.delete()
        audioFile = null
        pendingText = null
        pendingHideAfterSuccess = true
        pendingSelectInsertedText = true
        lastErrorMessage = null
        state = ImeState.Idle
        requestHideSelf(0)
    }

    private fun showError(message: String, error: TranscriptionError? = null) {
        state = ImeState.Error
        lastErrorMessage = message
        applyErrorStatusStyle()
        statusText.setText(R.string.status_error)
        errorDetailsInput.setText(message)
        errorDetailsInput.visibility = View.VISIBLE
        timerText.visibility = View.GONE
        levelBar.visibility = View.GONE
        languageButton.visibility = View.GONE
        stopButton.setText(R.string.button_retry)
        stopButton.isEnabled = audioFile?.exists() == true
        cancelButton.setText(R.string.button_cancel)
        cancelButton.isEnabled = true
        retryButton.setText(R.string.button_copy_error)
        retryButton.visibility = View.VISIBLE
        retryButton.isEnabled = message.isNotBlank()
        if (error == TranscriptionError.EmptyTranscript) {
            audioFile?.delete()
            audioFile = null
            stopButton.isEnabled = false
        }
    }

    private fun resetControls() {
        state = ImeState.Idle
        lastErrorMessage = null
        applyDefaultStatusStyle()
        clearErrorDetails()
        timerText.text = "00:00"
        levelBar.progress = 0
        languageButton.visibility = View.GONE
        retryButton.setText(R.string.button_retry)
        retryButton.visibility = View.GONE
    }

    private fun setupLanguageControls() {
        languageButton.setOnClickListener {
            showLanguageMenu()
        }
    }

    private fun refreshLanguageControls(settings: TranscriptionLanguageSettings) {
        favoriteLanguageCodes = settings.favoriteLanguageCodes
        selectedLanguageCode = if (settings.languageCode in favoriteLanguageCodes) {
            settings.languageCode
        } else {
            null
        }
        if (selectedLanguageCode != settings.languageCode) {
            settingsStore.saveTranscriptionLanguage(selectedLanguageCode)
        }
        languageButton.text = languageButtonLabel(selectedLanguageCode)
    }

    private fun showLanguageMenu() {
        val menu = PopupMenu(this, languageButton)
        favoriteLanguageCodes.forEachIndexed { index, code ->
            menu.menu.add(0, index, index, languageButtonLabel(code))
        }
        menu.setOnMenuItemClickListener { item ->
            selectedLanguageCode = favoriteLanguageCodes.getOrNull(item.itemId)
            saveLanguageSettings()
            true
        }
        menu.show()
    }

    private fun saveLanguageSettings() {
        settingsStore.saveTranscriptionLanguage(selectedLanguageCode)
        languageButton.text = languageButtonLabel(selectedLanguageCode)
    }

    private fun languageButtonLabel(languageCode: String?): String {
        return languageCode?.uppercase(Locale.ROOT) ?: getString(R.string.language_auto).uppercase(Locale.ROOT)
    }

    private fun updateRecordingFeedback() {
        val elapsed = ((System.currentTimeMillis() - startedAtMillis) / 1000).coerceAtLeast(0)
        timerText.text = "%02d:%02d".format(elapsed / 60, elapsed % 60)
        val amplitude = recorder.maxAmplitude.coerceIn(0, 32767)
        levelBar.progress = ((amplitude / 32767.0) * 100).roundToInt()
    }

    private fun shouldStartFreshRecording(): Boolean {
        return state == ImeState.Idle || state == ImeState.Inserted
    }

    private fun InputConnection?.safeCommitText(text: String, selectInsertedText: Boolean) {
        val inputConnection = this ?: return
        if (!selectInsertedText) {
            inputConnection.commitText(text, 1)
            return
        }
        val insertStart = inputConnection.currentSelectionStart()
        inputConnection.commitText(text, 1)
        if (insertStart != null) {
            inputConnection.setSelection(insertStart, insertStart + text.length)
        }
    }

    private fun InputConnection.currentSelectionStart(): Int? {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (extracted.selectionStart < 0 || extracted.selectionEnd < 0) {
            return null
        }
        return min(extracted.selectionStart, extracted.selectionEnd)
    }

    private fun switchToNextKeyboard(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { switchToNextInputMethod(false) }.getOrDefault(false)
        } else {
            false
        }
    }

    private fun copyLastError() {
        val message = lastErrorMessage.orEmpty()
        if (message.isBlank()) {
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.error_clip_label), message))
        Toast.makeText(this, R.string.error_copied, Toast.LENGTH_SHORT).show()
    }

    private fun applyDefaultStatusStyle() {
        statusText.gravity = android.view.Gravity.CENTER
        statusText.textSize = 20f
        statusText.typeface = Typeface.DEFAULT_BOLD
    }

    private fun applyErrorStatusStyle() {
        statusText.gravity = android.view.Gravity.START
        statusText.textSize = 18f
        statusText.typeface = Typeface.DEFAULT_BOLD
    }

    private fun clearErrorDetails() {
        errorDetailsInput.setText("")
        errorDetailsInput.visibility = View.GONE
        timerText.visibility = View.VISIBLE
        levelBar.visibility = View.VISIBLE
        retryButton.setText(R.string.button_retry)
    }

    private fun appVersionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        return getString(R.string.ime_version_format, info.versionName)
    }

    private fun localizedValidationMessage(validation: SettingsValidation): String {
        return when (validation.error) {
            SettingsValidationError.ApiTokenRequired -> getString(R.string.validation_api_token_required)
            SettingsValidationError.ModelRequired -> getString(R.string.validation_model_required)
            SettingsValidationError.ServerUrlRequired -> getString(R.string.validation_server_url)
            null -> validation.message ?: getString(R.string.setup_configure_openime)
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

    private enum class ImeState {
        Idle,
        Recording,
        Uploading,
        ReadyToInsert,
        Inserted,
        Error,
    }

    private companion object {
        const val TAG = "OpenVoiceIME"
    }
}
