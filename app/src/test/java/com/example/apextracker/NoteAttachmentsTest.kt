package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #127 — note attachment filename-list encoding. */
class NoteAttachmentsTest {

    @Test
    fun `round-trips a list`() {
        val files = listOf("img_a.jpg", "img_b.jpg")
        assertEquals(files, attachmentList(joinAttachments(files)))
    }

    @Test
    fun `blank and whitespace entries are dropped`() {
        assertEquals(emptyList<String>(), attachmentList(""))
        assertEquals(emptyList<String>(), attachmentList("\n  \n"))
        assertEquals(listOf("img_a.jpg"), attachmentList("img_a.jpg\n\n"))
    }

    @Test
    fun `add is idempotent and preserves order`() {
        val once = addAttachment("", "img_a.jpg")
        assertEquals(listOf("img_a.jpg"), attachmentList(once))
        val twice = addAttachment(once, "img_a.jpg")
        assertEquals(listOf("img_a.jpg"), attachmentList(twice))
        val two = addAttachment(once, "img_b.jpg")
        assertEquals(listOf("img_a.jpg", "img_b.jpg"), attachmentList(two))
    }

    @Test
    fun `remove drops only the named file`() {
        val both = joinAttachments(listOf("img_a.jpg", "img_b.jpg"))
        assertEquals(listOf("img_b.jpg"), attachmentList(removeAttachment(both, "img_a.jpg")))
        assertEquals(listOf("img_a.jpg", "img_b.jpg"), attachmentList(removeAttachment(both, "missing.jpg")))
    }
}
