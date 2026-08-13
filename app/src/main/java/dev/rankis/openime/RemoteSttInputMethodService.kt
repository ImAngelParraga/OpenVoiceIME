package dev.rankis.openime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import dev.rankis.openime.audio.AudioRecorder
import dev.rankis.openime.audio.RecordingAudioFocus
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class RemoteSttInputMethodService : android.inputmethodservice.InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val provider: SttProvider = OpenAiCompatibleProvider()

    private lateinit var recorder: AudioRecorder
    private lateinit var recordingAudioFocus: RecordingAudioFocus
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
    private lateinit var cursorButton: Button
    private lateinit var deleteButton: Button
    private lateinit var retryButton: Button

    private lateinit var cursorGesture: EditorGestureController
    private lateinit var deleteGesture: EditorGestureController
    private lateinit var cursorTrackpadGesture: CursorTrackpadController
    private lateinit var terminalDeleteGesture: TerminalGestureController

    private var state: ImeState = ImeState.Idle
    private var startedAtMillis = 0L
    private var audioFile: File? = null
    private var pendingText: String? = null
    private var pendingHideAfterSuccess: Boolean = true
    private var pendingSelectInsertedText: Boolean = true
    private var pendingReturnToKeyboardAfterInsert: Boolean = true
    private var lastErrorMessage: String? = null
    private var operationId: Long = 0L
    private var selectedLanguageCode: String? = null
    private var favoriteLanguageCodes: List<String?> = emptyList()
    private var currentEditorPackageName: String? = null
    private var inputViewVisible = false
    private var startRecordingScheduled = false
    private var activeEditorGesture: ActiveEditorGesture? = null
    private var activeGestureMode: GestureMode? = null
    private var terminalGestureDragged = false
    private var terminalDownX = 0f
    private var terminalDownY = 0f
    private var uploadJob: Job? = null

    private val tick = object : Runnable {
        override fun run() {
            if (state == ImeState.Recording) {
                updateRecordingFeedback()
                handler.postDelayed(this, 120)
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
        recorder = AudioRecorder(this)
        recordingAudioFocus = RecordingAudioFocus(this)
        settingsStore = SettingsStore(this)
        metricsStore = TranscriptionMetricsStore(this)
    }

    override fun onCreateInputView(): View {
        cleanupForInputViewRecreation()
        val view = LayoutInflater.from(this).inflate(R.layout.ime_voice_input, null)
        inputViewVisible = true
        versionText = view.findViewById(R.id.versionText)
        statusText = view.findViewById(R.id.statusText)
        errorDetailsInput = view.findViewById(R.id.errorDetailsInput)
        timerText = view.findViewById(R.id.timerText)
        levelBar = view.findViewById(R.id.levelBar)
        languageButton = view.findViewById(R.id.languageButton)
        stopButton = view.findViewById(R.id.stopButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        cursorButton = view.findViewById(R.id.cursorButton)
        deleteButton = view.findViewById(R.id.deleteButton)
        retryButton = view.findViewById(R.id.retryButton)

        val density = resources.displayMetrics.density
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        cursorGesture = EditorGestureController(
            kind = EditorGestureKind.Cursor,
            horizontalStepPixels = 18f * density,
            touchSlopPixels = touchSlop,
        )
        deleteGesture = EditorGestureController(
            kind = EditorGestureKind.Delete,
            horizontalStepPixels = 18f * density,
            touchSlopPixels = touchSlop,
        )
        cursorTrackpadGesture = CursorTrackpadController(
            horizontalStepPixels = 18f * density,
            verticalStepPixels = 18f * density,
            touchSlopPixels = touchSlop,
        )
        terminalDeleteGesture = TerminalGestureController(18f * density)

        stopButton.setOnClickListener { onPrimaryAction() }
        cancelButton.setOnClickListener { cancelCurrentWork() }
        cursorButton.setOnTouchListener { _, event -> handleCursorGesture(event) }
        deleteButton.setOnClickListener { handleDeleteTap() }
        deleteButton.setOnTouchListener { _, event -> handleDeleteGesture(event) }
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
        showLanguageControls()
        scheduleStartRecordingOrShowSetupError()
        return view
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        cleanupForInputViewRecreation()
        super.onConfigurationChanged(newConfig)
    }

    override fun onStartInput(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        updateCurrentEditorPackageName(info)
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateCurrentEditorPackageName(info)
        inputViewVisible = true
        if (::statusText.isInitialized && shouldStartFreshRecording()) {
            resetControls()
            showLanguageControls()
            scheduleStartRecordingOrShowSetupError()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        inputViewVisible = false
        cleanupForInputViewRecreation()
        recordingAudioFocus.abandon()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        cleanupForInputViewRecreation()
        recordingAudioFocus.abandon()
        scope.cancel()
        super.onDestroy()
    }

    private fun cleanupForInputViewRecreation() {
        inputViewVisible = false
        operationId += 1
        cancelScheduledStartRecording()
        handler.removeCallbacks(tick)
        uploadJob?.cancel()
        uploadJob = null

        if (::cursorGesture.isInitialized) {
            cursorGesture.cancel()
        }
        if (::deleteGesture.isInitialized) {
            deleteGesture.cancel()
        }
        if (::cursorTrackpadGesture.isInitialized) {
            cursorTrackpadGesture.cancel()
        }
        if (::terminalDeleteGesture.isInitialized) {
            terminalDeleteGesture.cancel()
        }
        activeEditorGesture = null
        activeGestureMode = null
        terminalGestureDragged = false

        if (::recorder.isInitialized) {
            recorder.cancel()
        }
        if (::recordingAudioFocus.isInitialized) {
            recordingAudioFocus.abandon()
        }
        audioFile?.delete()
        audioFile = null
        pendingText = null
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
            lastErrorMessage = null
            state = ImeState.Recording
            startedAtMillis = System.currentTimeMillis()
            applyDefaultStatusStyle()
            clearErrorDetails()
            statusText.setText(R.string.status_recording)
            timerText.text = "00:00"
            levelBar.progress = 0
            showLanguageControls()
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
            ImeState.Recording -> stopAndUpload()
            ImeState.ReadyToInsert -> commitPendingText()
            ImeState.Error -> retryUpload()
            else -> Unit
        }
    }

    private fun stopAndUpload() {
        handler.removeCallbacks(tick)
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
        upload(file)
    }

    private fun upload(file: File) {
        val settings = loadCurrentEditorSettings()
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

        uploadJob?.cancel()
        uploadJob = scope.launch {
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
        pendingReturnToKeyboardAfterInsert = settings.returnToKeyboardAfterInsert
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

    private fun cancelCurrentWork() {
        operationId += 1
        startRecordingScheduled = false
        handler.removeCallbacks(scheduledStartRecording)
        handler.removeCallbacks(tick)
        uploadJob?.cancel()
        uploadJob = null
        recorder.cancel()
        recordingAudioFocus.abandon()
        audioFile?.delete()
        audioFile = null
        pendingText = null
        pendingHideAfterSuccess = true
        pendingSelectInsertedText = true
        pendingReturnToKeyboardAfterInsert = true
        lastErrorMessage = null
        state = ImeState.Idle
        if (settingsStore.load().hideAfterCancel) {
            requestHideSelf(0)
        } else {
            resetControls()
            showLanguageControls()
            scheduleStartRecordingOrShowSetupError()
        }
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
        if (info != null) {
            currentEditorPackageName = info.packageName?.trim()?.ifBlank { null }
        }
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
        val elapsed = ((System.currentTimeMillis() - startedAtMillis) / 1000).coerceAtLeast(0)
        timerText.text = "%02d:%02d".format(elapsed / 60, elapsed % 60)
        val amplitude = recorder.maxAmplitude.coerceIn(0, 32767)
        levelBar.progress = ((amplitude / 32767.0) * 100).roundToInt()
    }

    private fun shouldStartFreshRecording(): Boolean {
        return state == ImeState.Idle || state == ImeState.Inserted
    }

    private fun handleCursorGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeGestureMode = if (usesTerminalFallback()) GestureMode.Terminal else GestureMode.Rich
                cursorTrackpadGesture.begin(event.x, event.y)
                if (activeGestureMode == GestureMode.Rich) {
                    val snapshot = currentEditorSnapshot()
                    if (snapshot == null) {
                        activeEditorGesture = null
                        cursorGesture.cancel()
                    } else {
                        activeEditorGesture = ActiveEditorGesture(
                            expectedText = snapshot.text,
                            expectedStartOffset = snapshot.startOffset,
                            originalSelection = snapshot.selection,
                            previewSelection = snapshot.selection,
                        )
                        cursorGesture.begin(snapshot, event.x, event.y)
                    }
                } else {
                    activeEditorGesture = null
                    cursorGesture.cancel()
                }
            }
            MotionEvent.ACTION_MOVE -> applyCursorTrackpadMove(event)
            MotionEvent.ACTION_UP -> {
                applyCursorTrackpadMove(event)
                if (activeGestureMode == GestureMode.Rich &&
                    cursorTrackpadGesture.axis == CursorTrackpadAxis.Horizontal
                ) {
                    applyEditorCommand(cursorGesture.finishHorizontal(event.x))
                } else {
                    cursorGesture.cancel()
                }
                activeEditorGesture = null
                activeGestureMode = null
                cursorTrackpadGesture.cancel()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (activeGestureMode == GestureMode.Rich &&
                    cursorTrackpadGesture.axis == CursorTrackpadAxis.Horizontal
                ) {
                    restoreActiveGesture()
                }
                cursorGesture.cancel()
                cursorTrackpadGesture.cancel()
                activeEditorGesture = null
                activeGestureMode = null
            }
        }
        return true
    }

    private fun applyCursorTrackpadMove(event: MotionEvent) {
        val update = cursorTrackpadGesture.move(event.x, event.y) ?: return
        when (update.axis) {
            CursorTrackpadAxis.Horizontal -> {
                if (activeGestureMode == GestureMode.Rich) {
                    applyGesturePreview(cursorGesture.moveHorizontal(event.x))
                } else {
                    sendCursorKeys(update)
                }
            }
            CursorTrackpadAxis.Vertical -> {
                if (activeGestureMode == GestureMode.Rich && activeEditorGesture != null) {
                    cursorGesture.cancel()
                    activeEditorGesture = null
                }
                sendCursorKeys(update)
            }
        }
    }

    private fun sendCursorKeys(update: CursorTrackpadUpdate) {
        val key = when (update.axis) {
            CursorTrackpadAxis.Horizontal -> if (update.stepDelta < 0) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT
            } else {
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            }
            CursorTrackpadAxis.Vertical -> if (update.stepDelta < 0) {
                android.view.KeyEvent.KEYCODE_DPAD_UP
            } else {
                android.view.KeyEvent.KEYCODE_DPAD_DOWN
            }
        }
        repeat(kotlin.math.abs(update.stepDelta)) { sendDownUpKeyEvents(key) }
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

    private fun currentEditorSnapshot(): EditorTextSnapshot? {
        val inputConnection = currentInputConnection ?: return null
        return try {
            val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0) ?: return null
            val text = extracted.text?.toString() ?: return null
            if (extracted.selectionStart < 0 || extracted.selectionEnd < 0) {
                return null
            }
            val offset = extracted.startOffset.coerceAtLeast(0)
            val start = min(extracted.selectionStart, extracted.selectionEnd).coerceIn(0, text.length)
            val end = max(extracted.selectionStart, extracted.selectionEnd).coerceIn(start, text.length)
            EditorTextSnapshot(text, EditorSelection(start, end), offset)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun InputConnection?.safeCommitText(text: String, selectInsertedText: Boolean): SelectionBounds? {
        val inputConnection = this ?: return null
        val selection = inputConnection.currentSelectionBounds()
        if (!selectInsertedText) {
            inputConnection.commitText(text, 1)
            return selection?.let { bounds ->
                SelectionBounds(bounds.start, bounds.start + text.length)
            }
        }
        inputConnection.commitText(text, 1)
        if (selection != null) {
            inputConnection.setSelection(selection.start, selection.start + text.length)
            return SelectionBounds(selection.start, selection.start + text.length)
        }
        return null
    }

    private fun InputConnection.currentSelectionBounds(): SelectionBounds? {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return null
        if (extracted.selectionStart < 0 || extracted.selectionEnd < 0) {
            return null
        }
        return SelectionBounds(
            start = min(extracted.selectionStart, extracted.selectionEnd) + extracted.startOffset.coerceAtLeast(0),
            end = max(extracted.selectionStart, extracted.selectionEnd) + extracted.startOffset.coerceAtLeast(0),
        )
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

    private data class SelectionBounds(
        val start: Int,
        val end: Int,
    )

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
