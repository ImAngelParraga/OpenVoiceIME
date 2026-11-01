package dev.rankis.openime

import android.text.InputFilter
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorCompatibilityTest {
    @Test
    fun testV35LongRecoveryContentKeepsActionsWithinMeasuredKeyboard() = withEditor { activity ->
        for (scale in listOf(1f, 1.3f)) {
            val config = Configuration(activity.resources.configuration).apply { fontScale = scale }
            val context = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_OpenVoiceIME)
            val root = LayoutInflater.from(context).inflate(R.layout.ime_voice_input, null)
            root.findViewById<TextView>(R.id.statusText).text = "Transcript ".repeat(500)
            root.findViewById<EditText>(R.id.errorDetailsInput).apply {
                setText("Recover this text ".repeat(500)); visibility = View.VISIBLE
            }
            root.findViewById<View>(R.id.retryButton).visibility = View.VISIBLE
            val width = (320 * context.resources.displayMetrics.density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            assertTrue(root.findViewById<View>(R.id.statusScroll).height > 0)
            for (id in listOf(R.id.stopButton, R.id.cancelButton, R.id.retryButton, R.id.keyboardButton)) {
                val action = root.findViewById<View>(id)
                val rect = android.graphics.Rect()
                action.getDrawingRect(rect)
                (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(action, rect)
                assertTrue("Action outside keyboard at scale $scale", rect.top >= 0 && rect.bottom <= root.height)
            }
        }
    }

    @Test
    fun testV39RealEditorInsertsUnicodeAndReplacesSelectionOnce() = withEditor { activity ->
        val editor = activity.editor
        editor.setText("before old after")
        editor.setSelection(7, 10)
        assertEquals(TranscriptCommitResult.Accepted, editor.onCreateInputConnection(EditorInfo()).commitTranscript("😀 voice", true))
        assertEquals("before 😀 voice after", editor.text.toString())
        assertEquals(7, editor.selectionStart)
        assertEquals(15, editor.selectionEnd)
    }

    @Test
    fun testV39TruncatedTranscriptIsNotReportedAsCompleteInsertion() = withEditor { activity ->
        val editor = activity.editor
        editor.setText("abc")
        editor.setSelection(3)
        editor.filters = arrayOf(InputFilter.LengthFilter(5))
        assertEquals(TranscriptCommitResult.Unconfirmed, editor.onCreateInputConnection(EditorInfo()).commitTranscript("long dictation", true))
        assertEquals("abclo", editor.text.toString())
    }

    @Test
    fun testV39ExistingCompositionIsFinishedBeforeDictation() = withEditor { activity ->
        val editor = activity.editor
        val connection = editor.onCreateInputConnection(EditorInfo())!!
        connection.setComposingText("existing", 1)
        assertEquals(TranscriptCommitResult.Accepted, connection.commitTranscript(" voice", false))
        assertEquals("existing voice", editor.text.toString())
    }

    @Test
    fun testV39PartialSnapshotUsesGlobalSelectionAndMissingSnapshotsStillInsert() = withEditor { activity ->
        val editor = activity.editor
        editor.setText("0123456789abcd")
        editor.setSelection(12)
        val actual = editor.onCreateInputConnection(EditorInfo())!!
        val partial = object : InputConnectionWrapper(actual, false) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText = ExtractedText().apply {
                text = editor.text.substring(10)
                startOffset = 10
                selectionStart = editor.selectionStart - 10
                selectionEnd = editor.selectionEnd - 10
            }
        }
        assertEquals(TranscriptCommitResult.Accepted, partial.commitTranscript("😀", true))
        assertEquals("0123456789ab😀cd", editor.text.toString())
        assertEquals(12, editor.selectionStart)
        assertEquals(14, editor.selectionEnd)
        editor.setSelection(editor.length())
        val terminal = object : InputConnectionWrapper(actual, false) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
        }
        assertEquals(TranscriptCommitResult.Accepted, terminal.commitTranscript(" terminal", true))
        assertEquals("0123456789ab😀cd terminal", editor.text.toString())
    }

    @Test
    fun testV39RejectedConnectionAndFailedSelectionDoNotRepeatCommit() = withEditor { activity ->
        val editor = activity.editor
        val actual = editor.onCreateInputConnection(EditorInfo())!!
        val rejected = object : InputConnectionWrapper(actual, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int) = false
        }
        assertEquals(TranscriptCommitResult.Rejected, rejected.commitTranscript("lost", false))
        assertEquals("", editor.text.toString())
        val noSelection = object : InputConnectionWrapper(actual, false) {
            override fun setSelection(start: Int, end: Int): Boolean = throw IllegalStateException("selection unsupported")
        }
        assertEquals(TranscriptCommitResult.Accepted, noSelection.commitTranscript("once", true))
        assertEquals("once", editor.text.toString())
    }

    @Test
    fun testV39InvalidExtractedSelectionCannotMoveCaretToWrongOffset() = withEditor { activity ->
        val editor = activity.editor
        val actual = editor.onCreateInputConnection(EditorInfo())!!
        val invalid = object : InputConnectionWrapper(actual, false) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
                text = "abc"
                startOffset = -1
                selectionStart = 50
                selectionEnd = 50
            }
        }
        assertNull(invalid.editorSnapshot())
    }

    private fun withEditor(test: (IntegrationHostActivity) -> Unit) {
        ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario -> scenario.onActivity(test) }
    }
}
