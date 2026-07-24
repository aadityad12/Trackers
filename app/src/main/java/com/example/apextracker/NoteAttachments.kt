package com.example.apextracker

/**
 * Pure encoding for a note's attachment list (Issue #127): a newline-separated list of filenames.
 * Newline (not comma) is the separator because it can't appear in the generated UUID filenames and
 * needs no escaping. Blank/whitespace entries are dropped so a trailing separator can't yield a
 * phantom attachment. Unit-tested in NoteAttachmentsTest.
 */
fun attachmentList(encoded: String): List<String> =
    encoded.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

fun joinAttachments(files: List<String>): String =
    files.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

/** Adds [file] to [encoded] unless already present; returns the new encoded string. */
fun addAttachment(encoded: String, file: String): String =
    joinAttachments((attachmentList(encoded) + file).distinct())

/** Removes [file] from [encoded]; returns the new encoded string. */
fun removeAttachment(encoded: String, file: String): String =
    joinAttachments(attachmentList(encoded).filterNot { it == file })
