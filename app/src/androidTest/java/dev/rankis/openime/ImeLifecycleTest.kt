package dev.rankis.openime

import android.Manifest
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
                for (id in listOf(R.id.deleteButton, R.id.cursorButton, R.id.stopButton, R.id.cancelButton)) {
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
    fun testV43HideRestoresOwnedCursorPreview() = withIme { ime ->
        main { ime.show() }; idle()
        main {
            ime.host.editor.setText("abcdef")
            ime.host.editor.setSelection(3)
            val pad = ime.view.findViewById<View>(R.id.cursorButton)
            val distance = 54f * ime.host.resources.displayMetrics.density
            touch(pad, MotionEvent.ACTION_DOWN, 1f, 1f)
            touch(pad, MotionEvent.ACTION_MOVE, 1f + distance, 1f)
            assertEquals(6, ime.host.editor.selectionStart)
            ime.service.onFinishInputView(false)
            assertEquals(3, ime.host.editor.selectionStart)
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
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
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
        fun destroy() { if (!destroyed) { destroyed = true; service.onDestroy() } }
    }

    private class TestIme(context: Context, val fakeRecorder: VoiceRecorder, val fakeProvider: SttProvider) : RemoteSttInputMethodService() {
        var connection: InputConnection? = null
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
        var lastFile: File? = null
        override val maxAmplitude = 0
        override fun start(): File {
            check(!active)
            active = true; starts += 1
            return File.createTempFile("integration-audio-", ".m4a", context.cacheDir).apply {
                writeBytes(byteArrayOf(1, 2, 3)); lastFile = this
            }
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
        override suspend fun transcribe(audioFile: File, settings: AppSettings, timeoutMillis: Long): TranscriptionResult {
            val reply = CompletableDeferred<TranscriptionResult>()
            requests.send(reply)
            return reply.await()
        }
        fun next() = runBlocking { withTimeout(3_000L) { requests.receive() } }
    }
}
