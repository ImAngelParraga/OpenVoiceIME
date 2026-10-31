package dev.rankis.openime

import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class TranscriptInserterTest {
    @Test
    fun testV31MissingConnectionDoesNotReportSuccess() {
        assertEquals(TranscriptCommitResult.Rejected, (null as InputConnection?).commitTranscript("hello", false))
    }

    @Test
    fun testV31RejectedOrThrowingCommitDoesNotReportSuccess() {
        assertEquals(TranscriptCommitResult.Rejected, connection { _, _ -> false }.commitTranscript("hello", false))
        assertEquals(TranscriptCommitResult.Unconfirmed, connection { _, _ -> throw IllegalStateException("closed") }.commitTranscript("hello", false))
    }

    @Test
    fun testV31SuccessfulCommitOccursOnceWithCorrectCursorPosition() {
        val calls = mutableListOf<String>()
        val connection = connection { method, args ->
            if (method == "getExtractedText") return@connection null
            calls += method
            assertEquals("commitText", method)
            assertEquals("hello 😀", args!![0])
            assertEquals(1, args[1])
            true
        }
        assertEquals(TranscriptCommitResult.Accepted, connection.commitTranscript("hello 😀", false))
        assertEquals(listOf("commitText"), calls)
    }

    @Test
    fun testV31UnavailableSelectionDoesNotPreventOrRepeatCommit() {
        var commitCount = 0
        val connection = connection { method, _ ->
            if (method == "commitText") {
                commitCount += 1
                true
            } else throw UnsupportedOperationException("no selection support")
        }
        assertEquals(TranscriptCommitResult.Accepted, connection.commitTranscript("hello", true))
        assertEquals(1, commitCount)
    }

    @Test
    fun testV31SilentlyIgnoredCommitIsUnconfirmedAndNeverRepeated() {
        val editor = FakeEditor()
        editor.ignoreCommit = true
        assertEquals(TranscriptCommitResult.Unconfirmed, insertTranscript(editor, "new", true))
        assertEquals(1, editor.commitCount)
        assertEquals(null, editor.selected)
    }

    @Test
    fun testV31SuccessfulReplacementUsesGlobalOffsetAndSurvivesSelectionFailure() {
        val editor = FakeEditor()
        editor.failSelection = true
        assertEquals(TranscriptCommitResult.Accepted, insertTranscript(editor, "😀", true))
        assertEquals("a😀c", editor.current.text)
        assertEquals(11 to 13, editor.selected)
        assertEquals(1, editor.commitCount)
    }

    @Test
    fun testV31ReplacingIdenticalSelectedTextIsAccepted() {
        val editor = FakeEditor()
        assertEquals(TranscriptCommitResult.Accepted, insertTranscript(editor, "b", false))
        assertEquals("abc", editor.current.text)
        assertEquals(1, editor.commitCount)
    }

    @Test
    fun testV31UnavailableReadbackAndEditorFormattingDoNotTriggerDuplicateInsertion() {
        val editor = FakeEditor()
        editor.hideReadback = true
        assertEquals(TranscriptCommitResult.Accepted, insertTranscript(editor, "x", false))
        assertEquals(1, editor.commitCount)
        val formatted = FakeEditor()
        formatted.transform = true
        assertEquals(TranscriptCommitResult.Accepted, insertTranscript(formatted, "x", false))
        assertEquals("aXc", formatted.current.text)
        assertEquals(1, formatted.commitCount)
    }

    private class FakeEditor : TranscriptEditor {
        var current = EditorTextSnapshot("abc", EditorSelection(1, 2), 10)
        var commitCount = 0
        var selected: Pair<Int, Int>? = null
        var ignoreCommit = false
        var failSelection = false
        var hideReadback = false
        var transform = false

        override fun snapshot(): EditorTextSnapshot? = if (hideReadback && commitCount > 0) null else current

        override fun commit(text: String): Boolean {
            commitCount += 1
            if (!ignoreCommit) {
                current = current.copy(text = current.text.replaceRange(
                    current.selection.start, current.selection.end, if (transform) text.uppercase() else text,
                ))
            }
            return true
        }

        override fun select(start: Int, end: Int) {
            selected = start to end
            if (failSelection) throw UnsupportedOperationException("selection unavailable")
        }
    }

    private fun connection(invoke: (String, Array<out Any?>?) -> Any?): InputConnection {
        return Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, args -> invoke(method.name, args) } as InputConnection
    }
}
