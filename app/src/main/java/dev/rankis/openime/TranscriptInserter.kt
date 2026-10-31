package dev.rankis.openime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlin.math.min
import kotlin.math.max

internal enum class TranscriptCommitResult { Accepted, Rejected, Unconfirmed }

internal interface TranscriptEditor {
    fun snapshot(): EditorTextSnapshot?
    fun commit(text: String): Boolean
    fun select(start: Int, end: Int)
}

internal fun InputConnection?.commitTranscript(text: String, selectInsertedText: Boolean): TranscriptCommitResult {
    val connection = this ?: return TranscriptCommitResult.Rejected
    return insertTranscript(object : TranscriptEditor {
        override fun snapshot(): EditorTextSnapshot? {
            val extracted = connection.getExtractedText(ExtractedTextRequest(), 0) ?: return null
            val content = extracted.text?.toString() ?: return null
            return EditorTextSnapshot(
                content,
                EditorSelection(min(extracted.selectionStart, extracted.selectionEnd), max(extracted.selectionStart, extracted.selectionEnd)),
                extracted.startOffset,
            )
        }

        override fun commit(text: String) = connection.commitText(text, 1)
        override fun select(start: Int, end: Int) { connection.setSelection(start, end) }
    }, text, selectInsertedText)
}

internal fun insertTranscript(editor: TranscriptEditor, text: String, selectInsertedText: Boolean): TranscriptCommitResult {
    val before = runCatching { editor.snapshot() }.getOrNull()
    val accepted = try {
        editor.commit(text)
    } catch (_: RuntimeException) {
        return TranscriptCommitResult.Unconfirmed
    }
    if (!accepted) return TranscriptCommitResult.Rejected

    // Some editors accept the request but leave their text unchanged.
    if (before != null) {
        val after = runCatching { editor.snapshot() }.getOrNull()
        val expected = before.text.replaceRange(before.selection.start, before.selection.end, text)
        if (after != null && after.startOffset == before.startOffset && after.text == before.text && expected != before.text) {
            return TranscriptCommitResult.Unconfirmed
        }
    }

    // Selection is optional; its failure must never cause a second insertion.
    if (selectInsertedText && before != null) runCatching {
        val start = before.globalPosition(before.selection.start)
        editor.select(start, start + text.length)
    }
    return TranscriptCommitResult.Accepted
}
