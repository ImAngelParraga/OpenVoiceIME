package dev.rankis.openime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.max

internal enum class TranscriptCommitResult { Accepted, Rejected, Unconfirmed }

internal interface TranscriptEditor {
    fun finishComposition() { }
    fun snapshot(): EditorTextSnapshot?
    fun commit(text: String): Boolean
    fun select(start: Int, end: Int)
}

internal fun InputConnection?.editorSnapshot(): EditorTextSnapshot? {
    val connection = this ?: return null
    return try {
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        val text = extracted.text?.toString() ?: return null
        EditorTextSnapshot(
            text,
            EditorSelection(min(extracted.selectionStart, extracted.selectionEnd), max(extracted.selectionStart, extracted.selectionEnd)),
            extracted.startOffset,
        )
    } catch (_: RuntimeException) {
        null
    }
}

internal fun InputConnection?.commitTranscript(text: String, selectInsertedText: Boolean): TranscriptCommitResult {
    val editor = transcriptEditor() ?: return TranscriptCommitResult.Rejected
    return insertTranscript(editor, text, selectInsertedText)
}

internal suspend fun InputConnection?.commitTranscriptAwaitingReadback(
    text: String,
    selectInsertedText: Boolean,
    canSelect: () -> Boolean,
): TranscriptCommitResult {
    val editor = transcriptEditor() ?: return TranscriptCommitResult.Rejected
    return insertTranscriptAwaitingReadback(editor, text, selectInsertedText, canSelect)
}

private fun InputConnection?.transcriptEditor(): TranscriptEditor? {
    val connection = this ?: return null
    return object : TranscriptEditor {
        override fun snapshot() = connection.editorSnapshot()
        override fun finishComposition() { connection.finishComposingText() }

        override fun commit(text: String) = connection.commitText(text, 1)
        override fun select(start: Int, end: Int) { connection.setSelection(start, end) }
    }
}

internal fun insertTranscript(editor: TranscriptEditor, text: String, selectInsertedText: Boolean): TranscriptCommitResult {
    val insertion = TranscriptInsertion(editor, text)
    val result = insertion.commit()
    if (result == TranscriptCommitResult.Accepted && selectInsertedText) insertion.select()
    return result
}

internal suspend fun insertTranscriptAwaitingReadback(
    editor: TranscriptEditor,
    text: String,
    selectInsertedText: Boolean,
    canSelect: () -> Boolean = { true },
    awaitReadback: suspend () -> Unit = { delay(100) },
): TranscriptCommitResult {
    val insertion = TranscriptInsertion(editor, text)
    var result = insertion.commit()
    // Remote editors may acknowledge the queued edit before exposing its text.
    if (insertion.accepted) {
        repeat(5) {
            if (result == TranscriptCommitResult.Unconfirmed) {
                awaitReadback()
                result = insertion.verify()
            }
        }
    }
    if (result == TranscriptCommitResult.Accepted && selectInsertedText && canSelect()) insertion.select()
    return result
}

private class TranscriptInsertion(private val editor: TranscriptEditor, private val text: String) {
    private var before: EditorTextSnapshot? = null
    private var sawMismatch = false
    var accepted = false
        private set

    fun commit(): TranscriptCommitResult {
        runCatching { editor.finishComposition() }
        before = runCatching { editor.snapshot() }.getOrNull()
        accepted = try {
            editor.commit(text)
        } catch (_: RuntimeException) {
            return TranscriptCommitResult.Unconfirmed
        }
        return if (accepted) verify() else TranscriptCommitResult.Rejected
    }

    fun verify(): TranscriptCommitResult {
        val original = before ?: return TranscriptCommitResult.Accepted
        val after = runCatching { editor.snapshot() }.getOrNull()
        val expected = original.text.replaceRange(original.selection.start, original.selection.end, text)
        if (after == null || after.startOffset != original.startOffset) {
            return if (sawMismatch) TranscriptCommitResult.Unconfirmed else TranscriptCommitResult.Accepted
        }
        if (after.text != expected) {
            sawMismatch = true
            return TranscriptCommitResult.Unconfirmed
        }
        return TranscriptCommitResult.Accepted
    }

    fun select() {
        val original = before ?: return
        // Selection failure must never cause another insertion.
        runCatching {
            val start = original.globalPosition(original.selection.start)
            editor.select(start, start + text.length)
        }
    }
}
