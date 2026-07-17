package com.example.apextracker

import android.content.Context
import android.content.Intent

/**
 * Pure serializer for sharing a single note as plain text: title heading, a blank line, then the
 * body. The note's `content` already carries its bullet glyphs (see `bulletSequence` in NoteView),
 * so it shares verbatim — no re-formatting here.
 *
 * `untitledLabel` is passed in rather than hard-coded so the "Untitled" fallback stays localized
 * (`R.string.notes_untitled`) while this function keeps its purity — same pattern the comment in
 * `BudgetCsvExport.resolveCategoryName` recommends. A body-only note (blank title) still gets the
 * heading; an empty note collapses to just the heading with no trailing blank line.
 */
fun buildNoteShareText(title: String, content: String, untitledLabel: String): String {
    val heading = title.ifBlank { untitledLabel }
    return if (content.isBlank()) heading else "$heading\n\n$content"
}

/**
 * Fires a `text/plain` share sheet for one note. Unlike `shareBudgetCsv` this sends the body inline
 * via `EXTRA_TEXT` (no FileProvider) — a single note's plain text drops straight into an email/message
 * body, which a file attachment can't. `EXTRA_SUBJECT` carries the heading for targets that use it
 * (e.g. email clients).
 */
fun shareNote(context: Context, title: String, content: String, untitledLabel: String) {
    val heading = title.ifBlank { untitledLabel }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, heading)
        putExtra(Intent.EXTRA_TEXT, buildNoteShareText(title, content, untitledLabel))
    }
    context.startActivity(Intent.createChooser(intent, heading))
}
