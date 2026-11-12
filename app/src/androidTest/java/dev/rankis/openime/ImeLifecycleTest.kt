package dev.rankis.openime

import android.content.Context
import android.content.res.Configuration
import android.text.InputFilter
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rankis.openime.audio.RecordingFocus
import dev.rankis.openime.audio.VoiceRecorder
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.settings.SettingsStore
import dev.rankis.openime.stt.SttProvider
import dev.rankis.openime.stt.TranscriptionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ImeLifecycleTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun testV60ShortcutInsertsAndHidesWithoutSwitchingOrChangingPreferences() = withIme { ime ->
        main {
            val store = SettingsStore(ime.host)
            store.save(store.load().copy(returnToKeyboardAfterInsert = true, confirmBeforeInsert = true))
            ime.show()
        }; idle()
        main { assertTrue(ime.service.finishDictationFromKeyboard()) }
        val request = ime.provider.next()
        main { assertTrue(ime.service.finishDictationFromKeyboard()) }
        assertEquals(1, ime.provider.calls)
        request.complete(TranscriptionResult.Success("keyboard dictation")); idle()
        main {
            assertEquals("keyboard dictation", ime.host.editor.text.toString())
            assertEquals(1, ime.service.hideRequests)
            assertEquals(0, ime.service.nextKeyboardRequests)
            assertEquals(0, ime.service.previousKeyboardRequests)
            assertEquals(1, ime.recorder.stops)
            assertEquals(1, ime.recorder.starts)
            val settings = SettingsStore(ime.host).load()
            assertTrue(settings.confirmBeforeInsert)
            assertTrue(settings.returnToKeyboardAfterInsert)
            assertFalse(settings.hideAfterSuccess)
        }
    }

    @Test
    fun testV60ShortcutFinalizesPausedRecordingWithoutResuming() = withIme { ime ->
        main { ime.show() }; idle()
        main {
            ime.pause()
            assertTrue(ime.service.finishDictationFromKeyboard())
        }
        ime.provider.next().complete(TranscriptionResult.Success("paused dictation")); idle()
        main {
            assertEquals("paused dictation", ime.host.editor.text.toString())
            assertEquals(1, ime.recorder.stops)
            assertEquals(0, ime.recorder.resumes)
            assertEquals(1, ime.service.hideRequests)
            assertEquals(0, ime.service.nextKeyboardRequests)
        }
    }

    @Test
    fun testV61ShortcutNetworkFailureKeepsPanelAndRetryUsesSameAudio() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main { assertTrue(ime.service.finishDictationFromKeyboard()) }
        ime.provider.next().complete(TranscriptionResult.Failure(dev.rankis.openime.stt.TranscriptionError.Timeout)); idle()
        main {
            assertEquals(0, ime.service.hideRequests)
            assertEquals(0, ime.service.nextKeyboardRequests)
            assertTrue(file.exists())
            assertTrue(ime.service.finishDictationFromKeyboard())
        }
        ime.provider.next().complete(TranscriptionResult.Success("retried dictation")); idle()
        main {
            assertSame(file, ime.provider.submittedFile)
            assertEquals("retried dictation", ime.host.editor.text.toString())
            assertEquals(1, ime.service.hideRequests)
            assertEquals(0, ime.service.nextKeyboardRequests)
            assertEquals(1, ime.recorder.stops)
        }
    }

    @Test
    fun testV61RejectedShortcutInsertionKeepsTranscriptAndNeverSendsEnter() = withIme { ime ->
        var reject = true
        var enterEvents = 0
        var editorActions = 0
        main {
            ime.service.connection = object : InputConnectionWrapper(ime.service.connection, false) {
                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
                    if (reject) false else super.commitText(text, newCursorPosition)
                override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
                    if (event.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                        event.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                    ) enterEvents++
                    return super.sendKeyEvent(event)
                }
                override fun performEditorAction(actionCode: Int): Boolean {
                    editorActions++
                    return super.performEditorAction(actionCode)
                }
            }
            ime.show()
        }; idle()
        main { assertTrue(ime.service.finishDictationFromKeyboard()) }
        ime.provider.next().complete(TranscriptionResult.Success("retained transcript")); idle()
        main {
            assertEquals("", ime.host.editor.text.toString())
            assertEquals(0, ime.service.hideRequests)
            assertEquals(0, ime.service.nextKeyboardRequests)
            assertTrue(ime.recorder.lastFile!!.exists())
            reject = false
            assertTrue(ime.service.finishDictationFromKeyboard())
        }; idle()
        main {
            assertEquals("retained transcript", ime.host.editor.text.toString())
            assertEquals(1, ime.provider.calls)
            assertEquals(1, ime.service.hideRequests)
            assertEquals(0, enterEvents)
            assertEquals(0, editorActions)
        }
    }

    @Test
    fun testV61TruncatedShortcutInsertionStaysOpenWithFullTranscript() = withIme { ime ->
        main {
            ime.host.editor.filters = arrayOf(InputFilter.LengthFilter(5))
            ime.show()
        }; idle()
        main { assertTrue(ime.service.finishDictationFromKeyboard()) }
        ime.provider.next().complete(TranscriptionResult.Success("full transcript survives"))
        android.os.SystemClock.sleep(700); idle()
        main {
            assertEquals("full ", ime.host.editor.text.toString())
            assertEquals("full transcript survives", ime.view.findViewById<EditText>(R.id.errorDetailsInput).text.toString())
            assertEquals(0, ime.service.hideRequests)
            assertEquals(0, ime.service.nextKeyboardRequests)
            assertTrue(ime.recorder.lastFile!!.exists())
        }
    }

    @Test
    fun testV61ShortcutIntentSurvivesUploadViewRecreation() = withIme { ime ->
        main { ime.show() }; idle()
        main { assertTrue(ime.service.finishDictationFromKeyboard()) }
        val request = ime.provider.next()
        main {
            ime.service.onConfigurationChanged(Configuration(ime.service.resources.configuration))
            ime.rebuild()
        }
        request.complete(TranscriptionResult.Success("survived recreation")); idle()
        main {
            assertEquals("survived recreation", ime.host.editor.text.toString())
            assertEquals(1, ime.service.hideRequests)
            assertEquals(0, ime.service.nextKeyboardRequests)
        }
    }

    @Test
    fun testV61DiscardClearsShortcutIntentForNextDictation() = withIme { ime ->
        main { ime.show() }; idle()
        main { assertTrue(ime.service.finishDictationFromKeyboard()) }
        val cancelled = ime.provider.next()
        main { ime.cancel(); ime.show() }; idle()
        cancelled.complete(TranscriptionResult.Success("discarded")); idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("normal confirmation")); idle()
        main {
            assertEquals("", ime.host.editor.text.toString())
            assertEquals(1, ime.service.hideRequests)
            assertEquals("normal confirmation", ime.view.findViewById<TextView>(R.id.statusText).text.toString())
        }
    }

    @Test
    fun testV61EditorChangeDropsShortcutCompletionWithoutClosingNewPanel() = withIme { ime ->
        main { ime.show() }; idle()
        main { assertTrue(ime.service.finishDictationFromKeyboard()) }
        val previousRequest = ime.provider.next()
        lateinit var nextEditor: EditText
        main {
            nextEditor = EditText(ime.host)
            nextEditor.setText("another field")
            nextEditor.setSelection(nextEditor.length())
            ime.service.connection = nextEditor.onCreateInputConnection(EditorInfo())
            ime.info = EditorInfo().apply { packageName = "editor.second"; fieldId = 200 }
            ime.service.onStartInput(ime.info, false)
            ime.show()
        }; idle()
        previousRequest.complete(TranscriptionResult.Success("old dictation")); idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("new confirmation")); idle()
        main {
            assertEquals("another field", nextEditor.text.toString())
            assertEquals("new confirmation", ime.view.findViewById<TextView>(R.id.statusText).text.toString())
            assertEquals(0, ime.service.hideRequests)
            assertEquals(0, ime.service.nextKeyboardRequests)
        }
    }

    @Test
    fun testV56VerbatimCommandSurvivesConfirmationAndViewRebuild() = withIme { ime ->
        main {
            val store = SettingsStore(ime.host)
            store.save(store.load().copy(appendTrailingSpace = true))
            ime.show()
        }; idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("mkdir Projects", verbatim = true)); idle()
        main {
            assertEquals("", ime.host.editor.text.toString())
            ime.service.onConfigurationChanged(Configuration(ime.service.resources.configuration))
            ime.rebuild()
        }; idle()
        main {
            assertEquals("mkdir Projects", ime.view.findViewById<TextView>(R.id.statusText).text.toString())
            ime.primary()
        }; idle()
        main { assertEquals("mkdir Projects", ime.host.editor.text.toString()) }
    }

    @Test
    fun testV56VerbatimCommandAutoInsertionHasNoTrailingSpaceOrEnter() = withIme { ime ->
        main {
            val store = SettingsStore(ime.host)
            store.save(store.load().copy(appendTrailingSpace = true, confirmBeforeInsert = false))
            ime.show()
        }; idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("/new", verbatim = true)); idle()
        main { assertEquals("/new", ime.host.editor.text.toString()) }
    }

    @Test
    fun testV55ImmediateVisibleInputRestartRetainsRecordingAndExplicitTranscribe() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main {
            ime.service.inputWindowShown = true
            ime.service.onFinishInputView(true)
            ime.service.onFinishInput()
            ime.service.onStartInput(ime.info, false)
            ime.show()
        }; idle()
        main {
            assertTrue(ime.recorder.active)
            assertSame(file, ime.recorder.lastFile)
            assertEquals(1, ime.recorder.starts)
            assertEquals(0, ime.recorder.stops)
            assertTrue(file.exists())
            ime.primary()
        }
        ime.provider.next().complete(TranscriptionResult.Success("continuous dictation")); idle()
        main { assertEquals("continuous dictation", ime.view.findViewById<TextView>(R.id.statusText).text.toString()) }
    }

    @Test
    fun testV55FinishingInputWithoutImmediateRestartDiscardsRecording() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main {
            ime.service.inputWindowShown = true
            ime.service.onFinishInputView(true)
            ime.service.onFinishInput()
        }; idle()
        assertFalse(ime.recorder.active)
        assertFalse(file.exists())
    }

    @Test
    fun testV55HiddenInputRestartDiscardsRecording() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main {
            ime.service.inputWindowShown = true
            ime.service.onFinishInputView(true)
            ime.service.onFinishInput()
            ime.service.inputWindowShown = false
            ime.service.onStartInput(ime.info, true)
        }; idle()
        assertFalse(ime.recorder.active)
        assertFalse(file.exists())
    }

    @Test
    fun testV55ImmediateRestartInDifferentFieldDiscardsRecording() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main {
            ime.service.inputWindowShown = true
            ime.service.onFinishInputView(true)
            ime.service.onFinishInput()
            ime.info = EditorInfo().apply { packageName = "editor.second"; fieldId = 200 }
            ime.service.onStartInput(ime.info, false)
        }; idle()
        assertFalse(ime.recorder.active)
        assertFalse(file.exists())
    }

    @Test
    fun testV55ImmediateVisibleRestartKeepsPauseAndCancelStillDiscards() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main {
            ime.pause()
            ime.service.inputWindowShown = true
            ime.service.onFinishInputView(true)
            ime.service.onFinishInput()
            ime.service.onStartInput(ime.info, false)
            ime.show()
        }; idle()
        main {
            assertTrue(ime.recorder.paused)
            assertEquals(1, ime.recorder.starts)
            assertSame(file, ime.recorder.lastFile)
            ime.cancel()
        }; idle()
        assertFalse(ime.recorder.active)
        assertFalse(file.exists())
    }

    @Test
    fun testV51AttachedKeyboardRendersRecordingAndPause() = withIme { ime ->
        main {
            (ime.host.editor.parent as android.view.ViewGroup).removeView(ime.host.editor)
            ime.host.editor.setText("Design review · Thursday\n\nMove the review to Tuesday.")
            val content = android.widget.LinearLayout(ime.host).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(ime.host.editor, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
                addView(ime.view, android.widget.LinearLayout.LayoutParams(-1, -2))
            }
            ime.host.setContentView(content)
            ime.show()
        }; idle()
        capturePreview("keyboard-recording.png", ime.view)
        main { ime.pause() }; idle()
        capturePreview("keyboard-paused.png", ime.view)
    }

    private fun capturePreview(name: String, view: View) {
        if (InstrumentationRegistry.getArguments().getString("captureScreenshots") != "true") return
        val rendered = java.util.concurrent.CountDownLatch(1)
        main { view.postOnAnimation { view.postOnAnimation { rendered.countDown() } } }
        check(rendered.await(2, java.util.concurrent.TimeUnit.SECONDS)) { "Preview did not draw" }
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        try { output.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { screenshot.recycle() }
    }

    @Test
    fun navigationInsetsKeepDeleteAboveSystemKeyboardSwitcher() = withIme { ime ->
        main {
            val density = ime.host.resources.displayMetrics.density
            for (bottomDp in listOf(24, 48)) {
                val bottom = (bottomDp * density).toInt()
                val insets = androidx.core.view.WindowInsetsCompat.Builder()
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), androidx.core.graphics.Insets.of(0, 0, 0, bottom))
                    .build()
                androidx.core.view.ViewCompat.dispatchApplyWindowInsets(ime.view, insets)
                val width = (360 * density).toInt()
                ime.view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                ime.view.layout(0, 0, width, ime.view.measuredHeight)
                assertEquals(bottom, ime.view.paddingBottom)
                for (id in listOf(R.id.deleteButton, R.id.pauseButton, R.id.stopButton, R.id.cancelButton)) {
                    val action = ime.view.findViewById<View>(id)
                    val bounds = android.graphics.Rect()
                    action.getDrawingRect(bounds)
                    (ime.view as android.view.ViewGroup).offsetDescendantRectToMyCoords(action, bounds)
                    assertTrue(bounds.bottom <= ime.view.height - bottom)
                }
            }
        }
    }

    @Test
    fun delayedEditorCommitDoesNotReappearAfterSwitchingApplications() = withIme { ime ->
        lateinit var nextEditor: EditText
        main {
            val store = SettingsStore(ime.host)
            store.save(store.load().copy(confirmBeforeInsert = false, hideAfterSuccess = true))
            val actual = ime.service.connection!!
            ime.service.connection = object : InputConnectionWrapper(actual, false) {
                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ actual.commitText(text, newCursorPosition) }, 150)
                    return true
                }
            }
            ime.show()
        }; idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("delivered once")); idle()
        val file = ime.recorder.lastFile!!
        main {
            assertFalse(ime.view.findViewById<View>(R.id.stopButton).isEnabled)
            ime.service.onFinishInputView(true)
            ime.service.onFinishInput()
            ime.info = EditorInfo().apply { packageName = "editor.second"; fieldId = 200 }
            nextEditor = EditText(ime.host).apply { setText("second application"); setSelection(length()) }
            ime.service.connection = nextEditor.onCreateInputConnection(EditorInfo())
            ime.service.onStartInput(ime.info, false)
            ime.show()
        }
        android.os.SystemClock.sleep(700); idle()
        main {
            assertEquals("delivered once", ime.host.editor.text.toString())
            assertEquals("second application", nextEditor.text.toString())
            assertEquals(nextEditor.length(), nextEditor.selectionStart)
            assertEquals(View.GONE, ime.view.findViewById<View>(R.id.retryButton).visibility)
            assertEquals(ime.host.getString(R.string.button_stop), ime.view.findViewById<Button>(R.id.stopButton).text.toString())
            assertTrue(ime.view.findViewById<View>(R.id.stopButton).isEnabled)
        }
        assertFalse(file.exists())
        assertEquals(2, ime.recorder.starts)
    }

    @Test
    fun testV39TruncationKeepsFullTranscriptAndDoesNotStartAnotherRecording() = withIme { ime ->
        main {
            val store = SettingsStore(ime.host)
            store.save(store.load().copy(confirmBeforeInsert = false))
            ime.host.editor.filters = arrayOf(InputFilter.LengthFilter(5))
            ime.show()
        }; idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("full transcript survives")); android.os.SystemClock.sleep(700); idle()
        main {
            assertEquals("full ", ime.host.editor.text.toString())
            assertEquals("full transcript survives", ime.view.findViewById<EditText>(R.id.errorDetailsInput).text.toString())
            assertEquals(View.VISIBLE, ime.view.findViewById<View>(R.id.retryButton).visibility)
        }
        assertTrue(ime.recorder.lastFile!!.exists())
        assertEquals(1, ime.recorder.starts)
    }

    @Test
    fun testV43HideRestoresOwnedDeletePreview() = withIme { ime ->
        main { ime.show() }; idle()
        main {
            ime.host.editor.setText("abcdef")
            ime.host.editor.setSelection(6)
            val pad = ime.view.findViewById<View>(R.id.deleteButton)
            val distance = 54f * ime.host.resources.displayMetrics.density
            touch(pad, MotionEvent.ACTION_DOWN, 1f, 1f)
            touch(pad, MotionEvent.ACTION_MOVE, 1f - distance, 1f)
            assertEquals(3, ime.host.editor.selectionStart)
            ime.service.onFinishInputView(false)
            assertEquals(6, ime.host.editor.selectionStart)
            assertEquals("abcdef", ime.host.editor.text.toString())
        }
    }

    @Test
    fun testV44NewServiceRemovesOnlyAbandonedRecordingFiles() {
        val context = instrumentation.targetContext
        val abandoned = File.createTempFile("openime-", ".m4a", context.cacheDir)
        val unrelated = File.createTempFile("other-", ".m4a", context.cacheDir)
        try {
            withIme { _ -> assertFalse(abandoned.exists()); assertTrue(unrelated.exists()) }
        } finally { abandoned.delete(); unrelated.delete() }
    }

    @Test
    fun testV37CreatingViewDoesNotRecordUntilItIsShown() = withIme { ime ->
        idle()
        assertEquals(0, ime.recorder.starts)
        main { ime.show() }
        idle()
        assertEquals(1, ime.recorder.starts)
        assertTrue(ime.recorder.active)
    }

    @Test
    fun testV67OwnedViewOnlyRebuildRetainsRecorderButConfigurationStopsCapture() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main {
            val target = android.content.ComponentName(ime.host, ime.service.javaClass).flattenToString()
            HardwareDictation.request(target, "typing/ime", { target }, { fail("Unexpected opening timeout") }) { true }
            HardwareDictation.shown(ime.service)
            ime.rebuild()
            assertTrue(ime.recorder.active)
            assertEquals(1, ime.recorder.starts)
            assertEquals(0, ime.recorder.stops)
            assertSame(file, ime.recorder.lastFile)
            assertTrue(file.exists())
            ime.service.onConfigurationChanged(Configuration(ime.service.resources.configuration))
            ime.rebuild()
            assertFalse(ime.recorder.active)
            assertEquals(1, ime.recorder.starts)
            assertEquals(1, ime.recorder.stops)
            assertSame(file, ime.recorder.lastFile)
            assertTrue(file.exists())
            HardwareDictation.finish(restorePrevious = false)
        }
    }

    @Test
    fun testV37RebuildRetainsUploadAndTranscriptWithoutStartingNewRecorder() = withIme { ime ->
        main { ime.show() }; idle()
        main { ime.primary() }
        val reply = ime.provider.next()
        val file = ime.recorder.lastFile!!
        main {
            ime.service.onConfigurationChanged(Configuration(ime.service.resources.configuration))
            ime.rebuild()
        }
        reply.complete(TranscriptionResult.Success("retained after rebuild")); idle()
        main { assertEquals("retained after rebuild", ime.view.findViewById<TextView>(R.id.statusText).text.toString()) }
        assertTrue(file.exists())
        assertEquals(1, ime.recorder.starts)
        assertFalse(ime.recorder.active)
    }

    @Test
    fun closingPanelCancelsUploadDiscardsAudioAndIgnoresLateReply() = withIme { ime ->
        main { ime.show() }; idle()
        main { ime.primary() }
        val oldReply = ime.provider.next()
        val file = ime.recorder.lastFile!!
        main { ime.service.onFinishInputView(false) }; idle()
        assertFalse(file.exists())
        oldReply.complete(TranscriptionResult.Success("stale")); idle()
        main { ime.show(restarting = true) }; idle()
        main {
            assertEquals("", ime.host.editor.text.toString())
            assertFreshPanel(ime)
        }
        assertEquals(2, ime.recorder.starts)
    }

    @Test
    fun closingRecordingDiscardsItEvenWhenReopeningSameField() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main { ime.service.onFinishInputView(false) }; idle()
        assertFalse(ime.recorder.active)
        assertEquals(0, ime.recorder.stops)
        assertFalse(file.exists())
        main { ime.show(restarting = true) }; idle()
        main { assertFreshPanel(ime) }
        assertEquals(2, ime.recorder.starts)
    }

    @Test
    fun pendingConfirmationDoesNotFollowUserIntoAnotherApplication() = withIme { ime ->
        main { ime.show() }; idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("old transcript")); idle()
        val file = ime.recorder.lastFile!!
        main {
            ime.service.onFinishInputView(true)
            ime.service.onFinishInput()
            ime.info = EditorInfo().apply { packageName = "editor.second"; fieldId = 200 }
            ime.service.onStartInput(ime.info, false)
            ime.show()
        }; idle()
        assertFalse(file.exists())
        main {
            assertEquals("", ime.host.editor.text.toString())
            assertFreshPanel(ime)
        }
    }

    @Test
    fun acceptedCommitWithPermanentlyStaleReadbackDoesNotFollowSameAppChatChange() = withIme { ime ->
        main {
            val store = SettingsStore(ime.host)
            store.save(store.load().copy(confirmBeforeInsert = false))
            val actual = ime.service.connection!!
            val stale = actual.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            ime.service.connection = object : InputConnectionWrapper(actual, false) {
                override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int) = stale
            }
            ime.show()
        }; idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("already delivered"))
        android.os.SystemClock.sleep(700); idle()
        val file = ime.recorder.lastFile!!
        main {
            assertEquals("already delivered", ime.host.editor.text.toString())
            assertEquals(View.VISIBLE, ime.view.findViewById<View>(R.id.retryButton).visibility)
            // Chat editors can reuse both the package and field ID.
            ime.service.onFinishInputView(false)
            ime.service.onFinishInput()
            ime.host.editor.setText("")
            ime.service.connection = ime.host.editor.onCreateInputConnection(EditorInfo())
            ime.service.onStartInput(ime.info, false)
            ime.show()
        }; idle()
        assertFalse(file.exists())
        main {
            assertEquals("", ime.host.editor.text.toString())
            assertFreshPanel(ime)
        }
    }

    @Test
    fun newInputSessionDiscardsOldWorkWithoutFinishCallbacks() = withIme { ime ->
        main { ime.show() }; idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("old confirmation")); idle()
        val file = ime.recorder.lastFile!!
        main {
            ime.service.onStartInput(ime.info, false)
            ime.show()
        }; idle()
        assertFalse(file.exists())
        main { assertFreshPanel(ime) }
    }

    @Test
    fun testV52PauseResumeKeepsOneFileAndOnlyTranscribesOnRequest() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile
        main { ime.pause() }
        val pausedTime = ime.view.findViewById<TextView>(R.id.timerText).text.toString()
        android.os.SystemClock.sleep(1_100L); idle()
        main {
            assertEquals(pausedTime, ime.view.findViewById<TextView>(R.id.timerText).text.toString())
            assertEquals(ime.host.getString(R.string.status_paused), ime.view.findViewById<TextView>(R.id.statusText).text.toString())
            assertTrue(ime.recorder.paused)
            assertEquals(0, ime.provider.calls)
            ime.pause()
            assertFalse(ime.recorder.paused)
            assertEquals(1, ime.recorder.starts)
            assertSame(file, ime.recorder.lastFile)
            ime.pause()
            ime.primary()
        }
        val reply = ime.provider.next()
        assertEquals(1, ime.provider.calls)
        assertEquals(1, ime.recorder.stops)
        assertEquals(2, ime.recorder.pauses)
        assertEquals(1, ime.recorder.resumes)
        assertSame(file, ime.provider.submittedFile)
        reply.complete(TranscriptionResult.Success("one combined transcript")); idle()
        main { assertEquals("one combined transcript", ime.view.findViewById<TextView>(R.id.statusText).text.toString()) }
    }

    @Test
    fun testV52PausedViewRecreationRemainsResumable() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile
        main {
            ime.pause()
            ime.service.onConfigurationChanged(Configuration(ime.service.resources.configuration))
            ime.rebuild()
        }; idle()
        main {
            assertTrue(ime.recorder.paused)
            assertEquals(ime.host.getString(R.string.button_resume), ime.view.findViewById<Button>(R.id.pauseButton).text.toString())
            assertEquals(1, ime.recorder.starts)
            assertEquals(0, ime.recorder.stops)
            assertSame(file, ime.recorder.lastFile)
            ime.pause()
            assertFalse(ime.recorder.paused)
        }
    }

    @Test
    fun testV52CancelPausedRecordingRestartsWithoutEditingText() = withIme { ime ->
        main {
            val store = SettingsStore(ime.host)
            store.save(store.load().copy(hideAfterCancel = false))
            ime.host.editor.setText("keep this text")
            ime.show()
        }; idle()
        val oldFile = ime.recorder.lastFile!!
        main { ime.pause(); ime.cancel() }; idle()
        main {
            assertFreshPanel(ime)
            assertEquals("keep this text", ime.host.editor.text.toString())
            assertEquals(0, ime.service.hideRequests)
        }
        assertFalse(oldFile.exists())
        assertEquals(2, ime.recorder.starts)
        assertNotSame(oldFile, ime.recorder.lastFile)
    }

    @Test
    fun testV52HidingPausedRecordingDiscardsItAndReleasesRecorder() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main { ime.pause(); ime.service.onFinishInputView(false) }; idle()
        assertFalse(ime.recorder.active)
        assertFalse(file.exists())
        main { ime.show(restarting = true) }; idle()
        main { assertFreshPanel(ime) }
    }

    @Test
    fun testV54DeleteDuringPauseEditsSelectionWithoutDiscardingAudio() = withIme { ime ->
        main { ime.show() }; idle()
        val file = ime.recorder.lastFile!!
        main {
            ime.pause()
            ime.host.editor.setText("keep bad dictation")
            ime.host.editor.setSelection(5, 18)
            ime.view.findViewById<Button>(R.id.deleteButton).performClick()
            assertEquals("keep ", ime.host.editor.text.toString())
            assertTrue(ime.recorder.paused)
            assertEquals(1, ime.recorder.starts)
            assertEquals(0, ime.provider.calls)
            assertTrue(file.exists())
        }
    }

    @Test
    fun testV54PauseFailureFinalizesRecoverableAudioWithoutUploading() = withIme { ime ->
        main { ime.show() }; idle()
        main {
            ime.recorder.failPause = true
            ime.pause()
            assertFalse(ime.recorder.active)
            assertTrue(ime.recorder.lastFile!!.exists())
            assertFalse(ime.view.findViewById<View>(R.id.pauseButton).isEnabled)
            assertTrue(ime.view.findViewById<View>(R.id.stopButton).isEnabled)
            assertEquals(ime.host.getString(R.string.button_retry), ime.view.findViewById<Button>(R.id.stopButton).text.toString())
            assertEquals(0, ime.provider.calls)
        }
    }

    @Test
    fun testV53StayOpenInsertionAutomaticallyRecordsAgainWithoutSwitching() = withIme { ime ->
        main {
            val store = SettingsStore(ime.host)
            store.save(store.load().copy(confirmBeforeInsert = false, hideAfterSuccess = false, returnToKeyboardAfterInsert = false))
            ime.show()
        }; idle()
        main { ime.primary() }
        ime.provider.next().complete(TranscriptionResult.Success("stay here")); idle()
        main {
            assertEquals("stay here", ime.host.editor.text.toString())
            assertFreshPanel(ime)
            assertEquals(0, ime.service.nextKeyboardRequests)
            assertEquals(0, ime.service.hideRequests)
            val stored = SettingsStore(ime.host).load()
            assertFalse(stored.returnToKeyboardAfterInsert)
            assertFalse(stored.hideAfterSuccess)
        }
        assertEquals(2, ime.recorder.starts)
    }

    @Test
    fun testV53ExplicitKeyboardReturnAndHidePreferencesRemainEffective() {
        for ((returnToKeyboard, hideAfterInsert) in listOf(true to false, false to true)) {
            withIme { ime ->
                main {
                    val store = SettingsStore(ime.host)
                    store.save(store.load().copy(confirmBeforeInsert = false, returnToKeyboardAfterInsert = returnToKeyboard, hideAfterSuccess = hideAfterInsert))
                    ime.show()
                }; idle()
                main { ime.primary() }
                ime.provider.next().complete(TranscriptionResult.Success("inserted")); idle()
                main {
                    assertEquals("inserted", ime.host.editor.text.toString())
                    assertEquals(if (returnToKeyboard) 1 else 0, ime.service.nextKeyboardRequests)
                    assertEquals(if (hideAfterInsert) 1 else 0, ime.service.hideRequests)
                    assertFalse(ime.recorder.active)
                }
                assertEquals(1, ime.recorder.starts)
            }
        }
    }

    @Test
    fun testV54ToolbarOpensSettingsAndSwitchesKeyboardDirectly() = withIme { ime ->
        main { ime.show() }; idle()
        main {
            ime.view.findViewById<View>(R.id.settingsButton).performClick()
            assertEquals(dev.rankis.openime.settings.SettingsActivity::class.java.name, ime.service.launchedActivity?.component?.className)
            ime.view.findViewById<View>(R.id.keyboardButton).performClick()
            assertEquals(1, ime.service.previousKeyboardRequests)
            assertFalse(ime.recorder.active)
        }
    }

    private fun assertFreshPanel(ime: Harness) {
        assertEquals(View.GONE, ime.view.findViewById<View>(R.id.retryButton).visibility)
        assertEquals(View.GONE, ime.view.findViewById<View>(R.id.errorDetailsInput).visibility)
        assertEquals(ime.host.getString(R.string.button_stop), ime.view.findViewById<Button>(R.id.stopButton).text.toString())
        assertTrue(ime.view.findViewById<View>(R.id.stopButton).isEnabled)
        assertTrue(ime.recorder.active)
    }

    @Test
    fun testV37DestroyDeletesAudioAndCancelsPendingWork() = withIme { ime ->
        main { ime.show() }; idle()
        main { ime.primary() }
        val reply = ime.provider.next()
        val file = ime.recorder.lastFile!!
        main { ime.destroy() }
        reply.complete(TranscriptionResult.Success("late")); idle()
        assertFalse(file.exists())
        assertFalse(ime.recorder.active)
        main { assertEquals("", ime.host.editor.text.toString()) }
    }

    private fun withIme(test: (Harness) -> Unit) {
        val context = instrumentation.targetContext
        DeviceTestPermissions.microphone(instrumentation.uiAutomation, context)
        var harness: Harness? = null
        ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario ->
            scenario.onActivity { host ->
                val settings = AppSettings(apiToken = "integration-test", confirmBeforeInsert = true, hideAfterSuccess = false, returnToKeyboardAfterInsert = false)
                SettingsStore(host).apply { save(settings); markConnectionTestSucceeded(settings) }
                harness = Harness(host)
            }
            try { test(harness!!) } finally { main { harness?.destroy() } }
        }
    }

    private fun idle() = instrumentation.waitForIdleSync()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, action, x, y, 0).also { event ->
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    private class Harness(val host: IntegrationHostActivity) {
        val recorder = FakeRecorder(host)
        val provider = PendingProvider()
        var info = EditorInfo().apply { packageName = "editor.first"; fieldId = 100 }
        val service = TestIme(host, recorder, provider)
        var view: View
        private var destroyed = false

        init {
            service.onCreate()
            service.connection = host.editor.onCreateInputConnection(EditorInfo())
            service.onStartInput(info, false)
            view = service.onCreateInputView()
        }

        fun show(restarting: Boolean = false) { service.onStartInputView(info, restarting) }
        fun rebuild() { view = service.onCreateInputView(); show(restarting = true) }
        fun primary() { view.findViewById<Button>(R.id.stopButton).performClick() }
        fun pause() { view.findViewById<Button>(R.id.pauseButton).performClick() }
        fun cancel() { view.findViewById<Button>(R.id.cancelButton).performClick() }
        fun destroy() { if (!destroyed) { destroyed = true; service.onDestroy() } }
    }

    private class TestIme(context: Context, val fakeRecorder: VoiceRecorder, val fakeProvider: SttProvider) : RemoteSttInputMethodService() {
        var connection: InputConnection? = null
        var inputWindowShown = false
        override fun isInputViewShown() = inputWindowShown
        var hideRequests = 0
        var nextKeyboardRequests = 0
        var previousKeyboardRequests = 0
        var nextKeyboardResult = true
        var launchedActivity: android.content.Intent? = null
        override fun requestHideSelf(flags: Int) { hideRequests += 1 }
        override fun switchToNextKeyboard(): Boolean { nextKeyboardRequests += 1; return nextKeyboardResult }
        override fun switchToPreviousOrFallbackKeyboard(): Boolean { previousKeyboardRequests += 1; return true }
        override fun startActivity(intent: android.content.Intent) { launchedActivity = intent }
        init { attachBaseContext(context) }
        override fun getCurrentInputConnection() = connection
        override fun createRecorder() = fakeRecorder
        override fun createSttProvider() = fakeProvider
        override fun createRecordingFocus() = object : RecordingFocus {
            override fun request() { }
            override fun abandon() { }
        }
    }

    private class FakeRecorder(private val context: Context) : VoiceRecorder {
        var starts = 0
        var stops = 0
        var active = false
        var paused = false
        var pauses = 0
        var resumes = 0
        var failPause = false
        var lastFile: File? = null
        override val maxAmplitude = 12_000
        override fun start(): File {
            check(!active)
            active = true; paused = false; starts += 1
            return File.createTempFile("integration-audio-", ".m4a", context.cacheDir).apply {
                writeBytes(byteArrayOf(1, 2, 3)); lastFile = this
            }
        }
        override fun pause() {
            check(active && !paused)
            if (failPause) error("Pause failed")
            paused = true; pauses += 1
        }
        override fun resume() {
            check(active && paused)
            paused = false; resumes += 1
        }
        override fun stop(): File? {
            if (!active) return null
            active = false; stops += 1
            return lastFile
        }
        override fun cancel() { if (active) { active = false; lastFile?.delete() } }
    }

    private class PendingProvider : SttProvider {
        private val requests = Channel<CompletableDeferred<TranscriptionResult>>(Channel.UNLIMITED)
        var calls = 0
        var submittedFile: File? = null
        override suspend fun transcribe(audioFile: File, settings: AppSettings, timeoutMillis: Long): TranscriptionResult {
            calls += 1
            submittedFile = audioFile
            val reply = CompletableDeferred<TranscriptionResult>()
            requests.send(reply)
            return reply.await()
        }
        fun next() = runBlocking { withTimeout(3_000L) { requests.receive() } }
    }
}
