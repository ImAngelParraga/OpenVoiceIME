package dev.rankis.openime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.rankis.openime.audio.AudioRecorder
import dev.rankis.openime.audio.RecordingDuration
import dev.rankis.openime.audio.RecordingAudioFocus
import dev.rankis.openime.audio.VoiceRecorder
import dev.rankis.openime.audio.RecordingFocus
import dev.rankis.openime.audio.deleteAbandonedRecordings
import dev.rankis.openime.metrics.TranscriptionMetricsStore
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.settings.SettingsActivity
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
import dev.rankis.openime.stt.transcriptionTimeoutMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class RemoteSttInputMethodService : android.inputmethodservice.InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var provider: SttProvider

    private lateinit var recorder: VoiceRecorder
    private lateinit var recordingAudioFocus: RecordingFocus
    private lateinit var settingsStore: SettingsStore
    private lateinit var metricsStore: TranscriptionMetricsStore
    private lateinit var statusText: TextView
    private lateinit var errorDetailsInput: EditText
    private lateinit var timerText: TextView
    private lateinit var levelBar: AudioLevelView
    private lateinit var languageButton: TextView
    private lateinit var stopButton: Button
    private lateinit var cancelButton: Button
    private lateinit var pauseButton: Button
    private lateinit var settingsButton: ImageButton
    private lateinit var deleteButton: Button
    private lateinit var keyboardButton: ImageButton
    private lateinit var retryButton: Button

    private lateinit var deleteGesture: EditorGestureController
    private lateinit var terminalDeleteGesture: TerminalGestureController

    private var state: ImeState = ImeState.Idle
    private val recordingDuration = RecordingDuration { SystemClock.elapsedRealtime() }
    private var audioDurationMillis = 0L
    private var uploadDeadlineMillis = 0L
    private var editorSessionId = 0L
    private var recordingEditorSessionId = 0L
    private var audioFile: File? = null
    private var pendingText: String? = null
    private var pendingInsertionMessage: String? = null
    private var pendingHideAfterSuccess: Boolean = true
    private var pendingSelectInsertedText: Boolean = true
    private var pendingReturnToKeyboardAfterInsert: Boolean = true
    private var lastErrorMessage: String? = null
    private var operationId: Long = 0L
    private var selectedLanguageCode: String? = null
    private var favoriteLanguageCodes: List<String?> = emptyList()
    private var currentEditorPackageName: String? = null
    private var inputViewVisible = false
    private var rebuildingInputView = false
    private var awaitingInputRestart = false
    private var currentEditorFieldId: Int? = null
    private var currentEditorInputType: Int? = null
    private var startRecordingScheduled = false
    private var activeEditorGesture: ActiveEditorGesture? = null
    private var activeGestureMode: GestureMode? = null
    private var terminalGestureDragged = false
    private var terminalDownX = 0f
    private var terminalDownY = 0f
    private var uploadJob: Job? = null

    private val finishUnrestartedInput = Runnable {
        if (awaitingInputRestart) discardCurrentWork()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (state == ImeState.Recording) {
                updateRecordingFeedback()
                handler.postDelayed(this, 120)
            } else if (state == ImeState.Uploading) {
                val remainingSeconds = ((uploadDeadlineMillis - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0L)
                timerText.text = getString(R.string.transcription_time_left_format, remainingSeconds / 60, remainingSeconds % 60)
                handler.postDelayed(this, 250)
            }
        }
    }

    private val scheduledStartRecording = Runnable {
        startRecordingScheduled = false
        if (inputViewVisible && shouldStartFreshRecording()) {
            startRecordingOrShowSetupError()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate() {
        super.onCreate()
        deleteAbandonedRecordings(cacheDir)
        provider = createSttProvider()
        recorder = createRecorder()
        recordingAudioFocus = createRecordingFocus()
        settingsStore = SettingsStore(this)
        metricsStore = TranscriptionMetricsStore(this)
        HardwareDictation.attach(this)
    }

    override fun onEvaluateInputViewShown(): Boolean =
        HardwareDictation.owns(this) || super.onEvaluateInputViewShown()

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean =
        HardwareDictation.owns(this) || super.onShowInputRequested(flags, configChange)

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (!rebuildingInputView) HardwareDictation.hidden(this)
    }

    protected open fun createSttProvider(): SttProvider = OpenAiCompatibleProvider()
    protected open fun createRecorder(): VoiceRecorder = AudioRecorder(this)
    protected open fun createRecordingFocus(): RecordingFocus = RecordingAudioFocus(this)

    @Suppress("DEPRECATION")
    private fun configureInputWindow() {
        val inputWindow = window?.window ?: return
        val background = ContextCompat.getColor(this, R.color.ime_background)
        inputWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background))
        inputWindow.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        inputWindow.navigationBarColor = background
        androidx.core.view.WindowCompat.getInsetsController(inputWindow, inputWindow.decorView)
            .isAppearanceLightNavigationBars =
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            inputWindow.isNavigationBarContrastEnforced = false
        }
    }

    override fun onCreateInputView(): View {
        detachInputView(preserveUpload = true)
        configureInputWindow()
        val view = LayoutInflater.from(this).inflate(R.layout.ime_voice_input, null)
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { ViewCompat.requestApplyInsets(v) }
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        statusText = view.findViewById(R.id.statusText)
        errorDetailsInput = view.findViewById(R.id.errorDetailsInput)
        errorDetailsInput.keyListener = null
        timerText = view.findViewById(R.id.timerText)
        levelBar = view.findViewById(R.id.levelBar)
        languageButton = view.findViewById(R.id.languageButton)
        stopButton = view.findViewById(R.id.stopButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        pauseButton = view.findViewById(R.id.pauseButton)
        settingsButton = view.findViewById(R.id.settingsButton)
        deleteButton = view.findViewById(R.id.deleteButton)
        keyboardButton = view.findViewById(R.id.keyboardButton)
        retryButton = view.findViewById(R.id.retryButton)

        val density = resources.displayMetrics.density
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        deleteGesture = EditorGestureController(
            kind = EditorGestureKind.Delete,
            horizontalStepPixels = 18f * density,
            touchSlopPixels = touchSlop,
        )
        terminalDeleteGesture = TerminalGestureController(18f * density)

        stopButton.setOnClickListener { onPrimaryAction() }
        cancelButton.setOnClickListener { cancelCurrentWork() }
        pauseButton.setOnClickListener { toggleRecordingPause() }
        settingsButton.setOnClickListener { openSettings() }
        deleteButton.setOnClickListener { handleDeleteTap() }
        deleteButton.setOnTouchListener { _, event -> handleDeleteGesture(event) }
        keyboardButton.setOnClickListener { returnToKeyboard() }
        retryButton.setOnClickListener {
            if (pendingText != null) {
                copyPendingTranscript()
            } else if (state == ImeState.Error) {
                copyLastError()
            } else {
                retryUpload()
            }
        }

        setupLanguageControls()
        renderCurrentState()
        return view
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        rebuildingInputView = true
        detachInputView(preserveUpload = true)
        try {
            super.onConfigurationChanged(newConfig)
        } finally {
            rebuildingInputView = false
        }
    }

    override fun onStartInput(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        val sameEditor = info != null && info.packageName == currentEditorPackageName &&
            info.fieldId == currentEditorFieldId && info.inputType == currentEditorInputType
        val finishingCapture = awaitingInputRestart
        val continuingCapture = finishingCapture && isInputViewShown && sameEditor
        cancelPendingInputFinish()
        if (!restarting || !sameEditor || finishingCapture) {
            if (rebuildingInputView) {
                detachInputView(preserveUpload = true, restoreGesture = false)
            } else if (!continuingCapture) {
                discardCurrentWork(restoreGesture = false)
            }
            editorSessionId += 1
        }
        updateCurrentEditorPackageName(info)
        currentEditorFieldId = info?.fieldId
        currentEditorInputType = info?.inputType
        HardwareDictation.showPending(this)
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateCurrentEditorPackageName(info)
        inputViewVisible = true
        HardwareDictation.shown(this)
        renderCurrentState()
        if (shouldStartFreshRecording()) {
            scheduleStartRecordingOrShowSetupError()
        }
        if (state == ImeState.Uploading || state == ImeState.Recording) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (rebuildingInputView) {
            detachInputView(preserveUpload = true)
        } else if (!finishingInput || !deferCaptureFinish()) {
            discardCurrentWork()
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        if (rebuildingInputView) {
            detachInputView(preserveUpload = true)
        } else if (!deferCaptureFinish()) {
            discardCurrentWork()
        }
        if (!rebuildingInputView) editorSessionId += 1
        super.onFinishInput()
    }

    override fun onDestroy() {
        HardwareDictation.detach(this)
        discardCurrentWork()
        recordingAudioFocus.abandon()
        scope.cancel()
        super.onDestroy()
    }

    private fun deferCaptureFinish(): Boolean {
        if (awaitingInputRestart) return true
        if (!inputViewVisible || !isInputViewShown ||
            (state != ImeState.Recording && state != ImeState.Paused)
        ) return false
        // System panels can synchronously finish and restart the still-visible editor.
        awaitingInputRestart = true
        inputViewVisible = false
        cancelScheduledStartRecording()
        handler.removeCallbacks(tick)
        runCatching { restoreActiveGesture() }
        clearEditorGestures()
        handler.post(finishUnrestartedInput)
        return true
    }

    private fun cancelPendingInputFinish() {
        awaitingInputRestart = false
        handler.removeCallbacks(finishUnrestartedInput)
    }

    private fun detachInputView(preserveUpload: Boolean = false, restoreGesture: Boolean = true, preserveRecording: Boolean = true) {
        inputViewVisible = false
        cancelScheduledStartRecording()
        handler.removeCallbacks(tick)
        if (restoreGesture) runCatching { restoreActiveGesture() }
        clearEditorGestures()

        if (::recorder.isInitialized) {
            val retainPausedRecorder = preserveRecording && state == ImeState.Paused
            if (preserveRecording && state == ImeState.Recording) {
                recordingDuration.pause()
                audioDurationMillis = recordingDuration.elapsedMillis.coerceAtLeast(1L)
                audioFile = recorder.stop()
                if (audioFile?.exists() == true && audioFile!!.length() > 0L) {
                    state = ImeState.ReadyToUpload
                } else {
                    state = ImeState.Error
                    lastErrorMessage = getString(R.string.error_recording_failed)
                }
            }
            if (!retainPausedRecorder) recorder.cancel()
        }
        if (::recordingAudioFocus.isInitialized) recordingAudioFocus.abandon()
        if (!preserveUpload && state == ImeState.Uploading) {
            operationId += 1
            uploadJob?.cancel()
            uploadJob = null
            state = ImeState.ReadyToUpload
        }
    }

    private fun clearEditorGestures() {
        if (::deleteGesture.isInitialized) {
            deleteGesture.cancel()
        }
        if (::terminalDeleteGesture.isInitialized) {
            terminalDeleteGesture.cancel()
        }
        activeEditorGesture = null
        activeGestureMode = null
        terminalGestureDragged = false
    }

    private fun discardCurrentWork(restoreGesture: Boolean = true) {
        cancelPendingInputFinish()
        detachInputView(restoreGesture = restoreGesture, preserveRecording = false)
        operationId += 1
        uploadJob?.cancel()
        uploadJob = null
        if (::recorder.isInitialized) {
            recorder.cancel()
        }
        if (::recordingAudioFocus.isInitialized) {
            recordingAudioFocus.abandon()
        }
        audioFile?.delete()
        audioFile = null
        audioDurationMillis = 0L
        recordingDuration.pause()
        pendingText = null
        pendingInsertionMessage = null
        pendingHideAfterSuccess = true
        pendingSelectInsertedText = true
        pendingReturnToKeyboardAfterInsert = true
        lastErrorMessage = null
        state = ImeState.Idle
    }

    private fun scheduleStartRecordingOrShowSetupError() {
        if (startRecordingScheduled) {
            return
        }
        startRecordingScheduled = true
        handler.post(scheduledStartRecording)
    }

    private fun cancelScheduledStartRecording() {
        startRecordingScheduled = false
        handler.removeCallbacks(scheduledStartRecording)
    }

    private fun startRecordingOrShowSetupError() {
        if (!inputViewVisible || !shouldStartFreshRecording()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showError(getString(R.string.setup_grant_microphone))
            return
        }

        runCatching {
            handler.removeCallbacks(tick)
            operationId += 1
            recordingAudioFocus.request()
            audioFile = recorder.start()
            pendingText = null
            pendingInsertionMessage = null
            lastErrorMessage = null
            state = ImeState.Recording
            recordingDuration.start()
            audioDurationMillis = 0L
            recordingEditorSessionId = editorSessionId
            applyDefaultStatusStyle()
            clearErrorDetails()
            statusText.setText(R.string.status_recording)
            timerText.text = "00:00"
            levelBar.progress = 0
            showLanguageControls()
            updatePauseControl()
            stopButton.setText(R.string.button_stop)
            stopButton.isEnabled = true
            cancelButton.setText(R.string.button_cancel)
            cancelButton.isEnabled = true
            retryButton.visibility = View.GONE
            handler.post(tick)
            validateRecordingSettingsAsync(operationId)
        }.onFailure {
            recorder.cancel()
            recordingAudioFocus.abandon()
            audioFile = null
            showError(getString(R.string.error_start_recorder))
        }
    }

    private fun onPrimaryAction() {
        when (state) {
            ImeState.Recording, ImeState.Paused -> {
                recordingEditorSessionId = editorSessionId
                stopAndUpload()
            }
            ImeState.ReadyToUpload -> retryUpload()
            ImeState.ReadyToInsert -> {
                recordingEditorSessionId = editorSessionId
                commitPendingText()
            }
            ImeState.Error -> retryUpload()
            else -> Unit
        }
    }

    private fun toggleRecordingPause() {
        if (state != ImeState.Recording && state != ImeState.Paused) return
        runCatching {
            if (state == ImeState.Recording) {
                recorder.pause()
                recordingDuration.pause()
                recordingAudioFocus.abandon()
                state = ImeState.Paused
                handler.removeCallbacks(tick)
            } else {
                recordingAudioFocus.request()
                recorder.resume()
                recordingDuration.resume()
                state = ImeState.Recording
                handler.post(tick)
            }
            renderCaptureState()
        }.onFailure {
            handler.removeCallbacks(tick)
            recordingDuration.pause()
            audioDurationMillis = recordingDuration.elapsedMillis.coerceAtLeast(1L)
            audioFile = recorder.stop()
            recordingAudioFocus.abandon()
            showError(getString(R.string.error_recording_pause))
        }
    }

    private fun renderCaptureState() {
        applyDefaultStatusStyle()
        clearErrorDetails()
        statusText.setText(if (state == ImeState.Paused) R.string.status_paused else R.string.status_recording)
        val elapsed = recordingDuration.elapsedMillis / 1000
        timerText.text = "%02d:%02d".format(elapsed / 60, elapsed % 60)
        if (state == ImeState.Paused) levelBar.progress = 0
        showLanguageControls()
        stopButton.setText(R.string.button_stop)
        stopButton.isEnabled = true
        cancelButton.setText(R.string.button_cancel)
        cancelButton.isEnabled = true
        retryButton.visibility = View.GONE
    }

    private fun updatePauseControl() {
        if (!::pauseButton.isInitialized) return
        pauseButton.isEnabled = state == ImeState.Recording || state == ImeState.Paused
        pauseButton.setText(if (state == ImeState.Paused) R.string.button_resume else R.string.button_pause)
        pauseButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (state == ImeState.Paused) R.drawable.ime_resume else R.drawable.ime_pause, 0, 0, 0,
        )
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun stopAndUpload() {
        handler.removeCallbacks(tick)
        recordingDuration.pause()
        audioDurationMillis = recordingDuration.elapsedMillis.coerceAtLeast(1L)
        val file = recorder.stop()
        recordingAudioFocus.abandon()
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
        recordingEditorSessionId = editorSessionId
        upload(file)
    }

    private fun upload(file: File) {
        val settings = loadCurrentEditorSettings()
        val readinessError = connectionReadinessError(settings)
        if (readinessError != null) {
            showError(readinessError)
            return
        }
        uploadJob?.cancel()
        operationId += 1
        val uploadOperationId = operationId
        val timeoutMillis = transcriptionTimeoutMillis(audioDurationMillis, file.length())
        uploadDeadlineMillis = SystemClock.elapsedRealtime() + timeoutMillis
        state = ImeState.Uploading
        lastErrorMessage = null
        applyDefaultStatusStyle()
        clearErrorDetails()
        languageButton.visibility = View.GONE
        statusText.text = getString(R.string.status_uploading_format, (file.length() / 1024).coerceAtLeast(1))
        stopButton.isEnabled = false
        cancelButton.isEnabled = true
        retryButton.visibility = View.GONE
        levelBar.visibility = View.GONE
        timerText.visibility = View.VISIBLE
        handler.removeCallbacks(tick)
        handler.post(tick)

        uploadJob = scope.launch {
            Log.i(TAG, "Upload started for ${file.length()} bytes")
            val audioBytes = file.length()
            val startedAt = SystemClock.elapsedRealtime()
            val result = try {
                withTimeout(timeoutMillis) { provider.transcribe(file, settings, timeoutMillis) }
            } catch (_: TimeoutCancellationException) {
                TranscriptionResult.Failure(TranscriptionError.Timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TranscriptionResult.Failure(TranscriptionError.Network(e.javaClass.simpleName))
            }
            val durationMillis = SystemClock.elapsedRealtime() - startedAt
            if (uploadOperationId != operationId) {
                return@launch
            }
            handler.removeCallbacks(tick)
            when (result) {
                is TranscriptionResult.Success -> handleTranscript(result.text, settings, result.verbatim)
                is TranscriptionResult.Failure -> showError(localizedTranscriptionError(result.error))
            }
            recordTranscriptionMetrics(
                audioBytes = audioBytes,
                durationMillis = durationMillis,
                success = result is TranscriptionResult.Success,
            )
        }
    }

    private suspend fun handleTranscript(text: String, settings: AppSettings, verbatim: Boolean) = withContext(Dispatchers.Main.immediate) {
        val commitText = formatCommitText(text, settings.appendTrailingSpace, verbatim)
        pendingHideAfterSuccess = settings.hideAfterSuccess
        pendingSelectInsertedText = settings.selectInsertedText
        pendingReturnToKeyboardAfterInsert = settings.returnToKeyboardAfterInsert
        pendingText = commitText
        pendingInsertionMessage = null
        if (!inputViewVisible) {
            state = ImeState.ReadyToInsert
            return@withContext
        }
        if (recordingEditorSessionId != editorSessionId) {
            showInsertionFailure(getString(R.string.transcription_editor_changed))
            return@withContext
        }
        if (settings.confirmBeforeInsert) {
            state = ImeState.ReadyToInsert
            renderCurrentState()
        } else {
            commitPendingText()
        }
    }

    private fun validateRecordingSettingsAsync(validationOperationId: Long) {
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                connectionReadinessError(settingsStore.load())
            }
            if (validationOperationId != operationId || state !in setOf(ImeState.Recording, ImeState.Paused) || error == null) {
                return@launch
            }
            handler.removeCallbacks(tick)
            recorder.cancel()
            recordingAudioFocus.abandon()
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

    private fun loadCurrentEditorSettings(): AppSettings {
        val settings = settingsStore.load()
        return settings.copy(
            transcriptionLanguageCode = settingsStore
                .loadTranscriptionLanguage(currentEditorPackageName)
                .languageCode,
        )
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
        if (!inputViewVisible) return
        if (recordingEditorSessionId != editorSessionId) {
            showInsertionFailure(getString(R.string.transcription_editor_changed))
            return
        }
        val insertionOperationId = operationId
        val insertionSessionId = editorSessionId
        val connection = currentInputConnection
        state = ImeState.VerifyingInsertion
        updatePauseControl()
        stopButton.isEnabled = false
        retryButton.visibility = View.GONE
        scope.launch {
            val insertionResult = connection.commitTranscriptAwaitingReadback(text, pendingSelectInsertedText) {
                inputViewVisible && editorSessionId == insertionSessionId && operationId == insertionOperationId
            }
            if (operationId != insertionOperationId) return@launch
            if (insertionResult != TranscriptCommitResult.Accepted) {
                showInsertionFailure(getString(
                    if (insertionResult == TranscriptCommitResult.Unconfirmed) R.string.transcription_insert_unconfirmed
                    else R.string.transcription_insert_failed,
                ))
                return@launch
            }
            audioFile?.delete()
            audioFile = null
            pendingText = null
            state = ImeState.Inserted
            lastErrorMessage = null
            pendingInsertionMessage = null
            if (!inputViewVisible || editorSessionId != insertionSessionId) {
                if (inputViewVisible) {
                    renderCurrentState()
                    scheduleStartRecordingOrShowSetupError()
                }
                return@launch
            }
            applyDefaultStatusStyle()
            clearErrorDetails()
            statusText.setText(R.string.status_inserted)
            stopButton.isEnabled = false
            retryButton.visibility = View.GONE
            if (pendingReturnToKeyboardAfterInsert) {
                val switched = switchToNextKeyboard()
                if (!switched && pendingHideAfterSuccess) {
                    requestHideSelf(0)
                }
            } else if (pendingHideAfterSuccess) {
                requestHideSelf(0)
            } else {
                resetControls()
                showLanguageControls()
                scheduleStartRecordingOrShowSetupError()
            }
        }
    }

    private fun showInsertionFailure(message: String) {
        state = ImeState.ReadyToInsert
        pendingInsertionMessage = message
        renderPendingTranscript()
    }

    private fun renderPendingTranscript() {
        if (!::statusText.isInitialized) return
        val changedEditor = recordingEditorSessionId != editorSessionId
        val message = if (changedEditor) getString(R.string.transcription_editor_changed) else pendingInsertionMessage
        if (message == null) {
            lastErrorMessage = null
            applyDefaultStatusStyle()
            clearErrorDetails()
            statusText.text = pendingText
            stopButton.setText(R.string.button_insert)
            stopButton.isEnabled = true
            cancelButton.setText(R.string.button_discard)
            cancelButton.isEnabled = true
            retryButton.visibility = View.GONE
            return
        }
        lastErrorMessage = null
        applyErrorStatusStyle()
        statusText.text = message
        errorDetailsInput.setText(pendingText)
        errorDetailsInput.visibility = View.VISIBLE
        timerText.visibility = View.GONE
        levelBar.visibility = View.GONE
        languageButton.visibility = View.GONE
        stopButton.setText(if (changedEditor) R.string.button_insert_here else R.string.button_insert)
        stopButton.isEnabled = true
        cancelButton.setText(R.string.button_discard)
        cancelButton.isEnabled = true
        retryButton.setText(R.string.button_copy_transcript)
        retryButton.visibility = View.VISIBLE
        retryButton.isEnabled = true
    }

    private fun copyPendingTranscript() {
        val text = pendingText ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.transcription_clip_label), text))
        Toast.makeText(this, R.string.transcription_copied, Toast.LENGTH_SHORT).show()
    }

    private fun cancelCurrentWork() {
        val wasVisible = inputViewVisible
        discardCurrentWork()
        if (settingsStore.load().hideAfterCancel) {
            requestHideSelf(0)
        } else {
            inputViewVisible = wasVisible
            resetControls()
            showLanguageControls()
            scheduleStartRecordingOrShowSetupError()
        }
    }

    private fun returnToKeyboard() {
        discardCurrentWork()
        if (switchToPreviousOrFallbackKeyboard()) {
            return
        }

        // Keep the panel usable if the platform refuses every switch request.
        inputViewVisible = true
        resetControls()
        showLanguageControls()
        scheduleStartRecordingOrShowSetupError()
    }

    private fun showError(message: String) {
        state = ImeState.Error
        lastErrorMessage = message
        renderError()
    }

    private fun renderError() {
        if (!::statusText.isInitialized) return
        val message = lastErrorMessage ?: getString(R.string.status_error)
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
    }

    private fun renderCurrentState() {
        if (!::statusText.isInitialized) return
        when (state) {
            ImeState.Idle, ImeState.Inserted -> {
                resetControls()
                showLanguageControls()
            }
            ImeState.Recording, ImeState.Paused -> renderCaptureState()
            ImeState.Uploading -> {
                applyDefaultStatusStyle()
                clearErrorDetails()
                statusText.text = getString(R.string.status_uploading_format, (audioFile?.length()?.div(1024) ?: 1).coerceAtLeast(1))
                levelBar.visibility = View.GONE
                languageButton.visibility = View.GONE
                stopButton.isEnabled = false
                cancelButton.isEnabled = true
                retryButton.visibility = View.GONE
            }
            ImeState.ReadyToUpload -> {
                applyDefaultStatusStyle()
                clearErrorDetails()
                statusText.setText(R.string.status_recording_saved)
                levelBar.visibility = View.GONE
                timerText.visibility = View.GONE
                showLanguageControls()
                stopButton.setText(R.string.button_send_audio)
                stopButton.isEnabled = audioFile?.exists() == true
                cancelButton.setText(R.string.button_discard)
                cancelButton.isEnabled = true
                retryButton.visibility = View.GONE
            }
            ImeState.VerifyingInsertion -> {
                stopButton.isEnabled = false
                retryButton.visibility = View.GONE
            }
            ImeState.ReadyToInsert -> renderPendingTranscript()
            ImeState.Error -> renderError()
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
            settingsStore.saveTranscriptionLanguage(selectedLanguageCode, currentEditorPackageName)
        }
        languageButton.text = languageButtonLabel(selectedLanguageCode)
    }

    private fun showLanguageControls() {
        refreshLanguageControls(settingsStore.loadTranscriptionLanguage(currentEditorPackageName))
        languageButton.visibility = View.VISIBLE
    }

    private fun showLanguageMenu() {
        if (shouldStartFreshRecording()) {
            cancelScheduledStartRecording()
        }
        val menu = PopupMenu(this, languageButton)
        favoriteLanguageCodes.forEachIndexed { index, code ->
            menu.menu.add(0, index, index, languageButtonLabel(code))
        }
        menu.setOnMenuItemClickListener { item ->
            selectedLanguageCode = favoriteLanguageCodes.getOrNull(item.itemId)
            saveLanguageSettings()
            scheduleStartRecordingIfFresh()
            true
        }
        menu.setOnDismissListener {
            scheduleStartRecordingIfFresh()
        }
        menu.show()
    }

    private fun saveLanguageSettings() {
        settingsStore.saveTranscriptionLanguage(selectedLanguageCode, currentEditorPackageName)
        languageButton.text = languageButtonLabel(selectedLanguageCode)
    }

    private fun updateCurrentEditorPackageName(info: android.view.inputmethod.EditorInfo?) {
        currentEditorPackageName = info?.packageName?.trim()?.ifBlank { null }
    }

    private fun scheduleStartRecordingIfFresh() {
        if (inputViewVisible && shouldStartFreshRecording()) {
            scheduleStartRecordingOrShowSetupError()
        }
    }

    private fun languageButtonLabel(languageCode: String?): String {
        return languageCode?.uppercase(Locale.ROOT) ?: getString(R.string.language_auto).uppercase(Locale.ROOT)
    }

    private fun updateRecordingFeedback() {
        val elapsed = recordingDuration.elapsedMillis / 1000
        timerText.text = "%02d:%02d".format(elapsed / 60, elapsed % 60)
        val amplitude = recorder.maxAmplitude.coerceIn(0, 32767)
        levelBar.progress = ((amplitude / 32767.0) * 100).roundToInt()
    }

    private fun shouldStartFreshRecording(): Boolean {
        return state == ImeState.Idle || state == ImeState.Inserted
    }

    private fun handleDeleteGesture(event: MotionEvent): Boolean {
        return handleEditorGesture(deleteGesture, event)
    }

    private fun handleEditorGesture(controller: EditorGestureController, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            activeGestureMode = if (usesTerminalFallback()) GestureMode.Terminal else GestureMode.Rich
        }
        if (activeGestureMode == GestureMode.Terminal) {
            return handleTerminalDeleteGesture(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val snapshot = currentEditorSnapshot()
                if (snapshot == null) {
                    activeEditorGesture = null
                    controller.cancel()
                } else {
                    activeEditorGesture = ActiveEditorGesture(
                        expectedText = snapshot.text,
                        expectedStartOffset = snapshot.startOffset,
                        originalSelection = snapshot.selection,
                        previewSelection = snapshot.selection,
                    )
                    controller.begin(snapshot, event.x, event.y)
                }
            }
            MotionEvent.ACTION_MOVE -> applyGesturePreview(controller.move(event.x, event.y))
            MotionEvent.ACTION_UP -> {
                val command = controller.finish(event.x, event.y)
                if (command is EditorGestureCommand.DeleteRange &&
                    command.fromTap
                ) {
                    activeEditorGesture = null
                    deleteButton.performClick()
                } else {
                    applyEditorCommand(command)
                    activeEditorGesture = null
                }
                activeGestureMode = null
            }
            MotionEvent.ACTION_CANCEL -> {
                controller.cancel()
                restoreActiveGesture()
                activeEditorGesture = null
                activeGestureMode = null
            }
        }
        return true
    }

    private fun usesTerminalFallback(): Boolean {
        if (currentInputEditorInfo?.inputType == InputType.TYPE_NULL) return true
        return currentEditorSnapshot() == null
    }

    private fun handleTerminalDeleteGesture(event: MotionEvent): Boolean {
        val gesture = terminalDeleteGesture
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture.begin(event.x)
                terminalDownX = event.x
                terminalDownY = event.y
                terminalGestureDragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - terminalDownX
                val dy = event.y - terminalDownY
                if (kotlin.math.abs(dy) > ViewConfiguration.get(this).scaledTouchSlop &&
                    kotlin.math.abs(dy) >= kotlin.math.abs(dx)
                ) {
                    gesture.cancel()
                    terminalGestureDragged = true
                } else if (kotlin.math.abs(dx) >= ViewConfiguration.get(this).scaledTouchSlop) {
                    terminalGestureDragged = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!terminalGestureDragged) {
                    deleteButton.performClick()
                } else {
                    sendTerminalDeleteKeys(gesture.deleteCount(event.x))
                }
                gesture.cancel()
                activeGestureMode = null
            }
            MotionEvent.ACTION_CANCEL -> {
                gesture.cancel()
                activeGestureMode = null
            }
        }
        return true
    }

    private fun sendTerminalDeleteKeys(count: Int) {
        repeat(count.coerceIn(0, 64)) { sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL) }
    }

    private fun handleDeleteTap() {
        val mode = resolveDeleteTapMode(activeGestureMode?.let {
            if (it == GestureMode.Terminal) GestureModeForTap.Terminal else GestureModeForTap.Rich
        }) { usesTerminalFallback() }
        if (mode == GestureModeForTap.Terminal) {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
            return
        }
        val snapshot = currentEditorSnapshot() ?: return
        activeEditorGesture = ActiveEditorGesture(
            expectedText = snapshot.text,
            expectedStartOffset = snapshot.startOffset,
            originalSelection = snapshot.selection,
            previewSelection = snapshot.selection,
        )
        applyEditorCommand(EditorGestureController.deleteTap(snapshot))
        activeEditorGesture = null
    }

    private fun applyGesturePreview(command: EditorGestureCommand) {
        when (command) {
            is EditorGestureCommand.PreviewSelection -> {
                applyOwnedSelection(command.selection, command.expected)
            }
            is EditorGestureCommand.RestoreSelection -> {
                applyOwnedSelection(command.selection, command.expected)
                activeEditorGesture?.valid = false
            }
            else -> Unit
        }
    }

    private fun applyEditorCommand(command: EditorGestureCommand) {
        val inputConnection = currentInputConnection ?: return
        when (command) {
            is EditorGestureCommand.MoveCaret -> {
                if (command.position < 0) {
                    return
                }
                if (!applyOwnedSelection(EditorSelection(command.position, command.position), command.expected)) {
                    return
                }
            }
            is EditorGestureCommand.DeleteRange -> {
                val current = currentEditorSnapshot() ?: return
                if (command.start < 0 || command.end > current.text.length || command.start >= command.end) {
                    return
                }
                if (!applyOwnedSelection(EditorSelection(command.start, command.end), command.expected)) {
                    return
                }
                inputConnection.commitText("", 1)
            }
            is EditorGestureCommand.PreviewSelection,
            is EditorGestureCommand.RestoreSelection,
            EditorGestureCommand.NoOp -> return
        }
    }

    private fun applyOwnedSelection(selection: EditorSelection, expected: EditorTextSnapshot): Boolean {
        val active = activeEditorGesture ?: return false
        val inputConnection = currentInputConnection ?: run {
            active.valid = false
            return false
        }
        val current = currentEditorSnapshot() ?: run {
            active.valid = false
            return false
        }
        if (
            !active.valid ||
            current.text != active.expectedText ||
            current.startOffset != active.expectedStartOffset ||
            expected.text != active.expectedText ||
            expected.startOffset != active.expectedStartOffset ||
            expected.selection != active.originalSelection ||
            current.selection != active.previewSelection ||
            selection.start < 0 ||
            selection.end < selection.start ||
            selection.end > current.text.length
        ) {
            active.valid = false
            return false
        }
        if (current.selection != selection && !inputConnection.setSelection(
                current.globalPosition(selection.start),
                current.globalPosition(selection.end),
            )) {
            active.valid = false
            return false
        }
        active.previewSelection = selection
        return true
    }

    private fun restoreActiveGesture() {
        val active = activeEditorGesture ?: return
        if (!active.valid) {
            return
        }
        val current = currentEditorSnapshot() ?: run {
            active.valid = false
            return
        }
        if (current.text != active.expectedText ||
            current.startOffset != active.expectedStartOffset ||
            current.selection != active.previewSelection
        ) {
            active.valid = false
            return
        }
        if (current.selection != active.originalSelection) {
            val inputConnection = currentInputConnection ?: run {
                active.valid = false
                return
            }
            if (!inputConnection.setSelection(
                    current.globalPosition(active.originalSelection.start),
                    current.globalPosition(active.originalSelection.end),
                )) {
                active.valid = false
                return
            }
        }
        active.previewSelection = active.originalSelection
    }

    private fun currentEditorSnapshot(): EditorTextSnapshot? = currentInputConnection.editorSnapshot()

    protected open fun switchToNextKeyboard(): Boolean {
        if (HardwareDictation.finish(this)) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { switchToNextInputMethod(false) }.getOrDefault(false)
        } else {
            false
        }
    }

    protected open fun switchToPreviousOrFallbackKeyboard(): Boolean {
        if (HardwareDictation.finish(this)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            runCatching { switchToPreviousInputMethod() }.getOrDefault(false)
        ) {
            return true
        }

        val otherInputMethods = enabledOtherInputMethods()
        if (otherInputMethods.size == 1) {
            // Do not use next-IME cycling here: one candidate must be deterministic.
            return runCatching {
                switchInputMethod(otherInputMethods.single().id)
                true
            }.getOrDefault(false)
        }

        return switchToNextKeyboard()
    }

    private fun enabledOtherInputMethods(): List<android.view.inputmethod.InputMethodInfo> {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return emptyList()
        return runCatching {
            inputMethodManager.enabledInputMethodList
                .asSequence()
                .filter { it.packageName != packageName }
                .distinctBy { it.id }
                .toList()
        }.getOrDefault(emptyList())
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
        updatePauseControl()
        statusText.gravity = android.view.Gravity.START
        statusText.textSize = 16f
        statusText.typeface = Typeface.DEFAULT_BOLD
    }

    private fun applyErrorStatusStyle() {
        updatePauseControl()
        statusText.gravity = android.view.Gravity.START
        statusText.textSize = 16f
        statusText.typeface = Typeface.DEFAULT_BOLD
    }

    private fun clearErrorDetails() {
        errorDetailsInput.setText("")
        errorDetailsInput.visibility = View.GONE
        timerText.visibility = View.VISIBLE
        levelBar.visibility = View.VISIBLE
        retryButton.setText(R.string.button_retry)
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
            TranscriptionError.Timeout -> getString(R.string.transcription_timeout_retry)
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
        Paused,
        ReadyToUpload,
        Uploading,
        ReadyToInsert,
        VerifyingInsertion,
        Inserted,
        Error,
    }

    private data class ActiveEditorGesture(
        val expectedText: String,
        val expectedStartOffset: Int,
        val originalSelection: EditorSelection,
        var previewSelection: EditorSelection,
        var valid: Boolean = true,
    )

    private enum class GestureMode { Rich, Terminal }

    private companion object {
        const val TAG = "OpenVoiceIME"
    }
}
