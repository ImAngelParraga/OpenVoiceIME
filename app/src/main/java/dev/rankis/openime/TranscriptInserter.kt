package dev.rankis.openime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
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
    val connection = this ?: return TranscriptCommitResult.Rejected
    return insertTranscript(object : TranscriptEditor {
        override fun snapshot() = connection.editorSnapshot()
        override fun finishComposition() { connection.finishComposingText() }

        override fun commit(text: String) = connection.commitText(text, 1)
        override fun select(start: Int, end: Int) { connection.setSelection(start, end) }
    }, text, selectInsertedText)
}

internal fun insertTranscript(editor: TranscriptEditor, text: String, selectInsertedText: Boolean): TranscriptCommitResult {
    runCatching { editor.finishComposition() }
    val before = runCatching { editor.snapshot() }.getOrNull()
    val accepted = try {
        editor.commit(text)
    } catch (_: RuntimeException) {
        return TranscriptCommitResult.Unconfirmed
    }
    if (!accepted) return TranscriptCommitResult.Rejected

    // Acceptance can hide truncation, filtering, or an ignored request.
    if (before != null) {
        val after = runCatching { editor.snapshot() }.getOrNull()
        val expected = before.text.replaceRange(before.selection.start, before.selection.end, text)
        if (after != null && after.startOffset == before.startOffset && after.text != expected) {
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
