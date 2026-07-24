package com.example.apextracker

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID

private const val ATTACH_TAG = "NoteAttachments"
private const val ATTACH_DIR = "note_attachments"

/** The app-private directory holding note image attachments, created on demand. */
private fun attachmentsDir(context: Context): File =
    File(context.filesDir, ATTACH_DIR).apply { if (!exists()) mkdirs() }

/** The [File] for a stored attachment [filename] (no existence guarantee). */
fun noteAttachmentFile(context: Context, filename: String): File =
    File(attachmentsDir(context), filename)

/**
 * Copies the picked [uri]'s bytes into app-private storage and returns the generated filename, or
 * null if the copy fails. The photo-picker grants only transient read access to [uri], so the note
 * has to own its own copy — the original may be unavailable later (Issue #127).
 */
fun saveNoteAttachment(context: Context, uri: Uri): String? {
    val filename = "img_${UUID.randomUUID()}.jpg"
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            noteAttachmentFile(context, filename).outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null
        filename
    } catch (e: Exception) {
        Log.w(ATTACH_TAG, "Failed to copy attachment from $uri", e)
        null
    }
}

/** Deletes a stored attachment file; no-ops if it's already gone. */
fun deleteNoteAttachment(context: Context, filename: String) {
    try {
        noteAttachmentFile(context, filename).delete()
    } catch (e: Exception) {
        Log.w(ATTACH_TAG, "Failed to delete attachment $filename", e)
    }
}
