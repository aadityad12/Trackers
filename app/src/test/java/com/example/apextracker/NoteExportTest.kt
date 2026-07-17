package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteExportTest {

    @Test
    fun `title and body are separated by a blank line`() {
        assertEquals(
            "Groceries\n\nMilk and eggs",
            buildNoteShareText("Groceries", "Milk and eggs", "Untitled")
        )
    }

    @Test
    fun `blank title falls back to the untitled label`() {
        assertEquals(
            "Untitled\n\nSome content",
            buildNoteShareText("", "Some content", "Untitled")
        )
    }

    @Test
    fun `whitespace-only title falls back to the untitled label`() {
        assertEquals(
            "Untitled\n\nSome content",
            buildNoteShareText("   ", "Some content", "Untitled")
        )
    }

    @Test
    fun `empty note collapses to the heading with no trailing blank line`() {
        assertEquals("Untitled", buildNoteShareText("", "", "Untitled"))
    }

    @Test
    fun `title-only note has no trailing blank line`() {
        assertEquals("Shopping", buildNoteShareText("Shopping", "", "Untitled"))
    }

    @Test
    fun `blank body collapses to the heading`() {
        assertEquals("Shopping", buildNoteShareText("Shopping", "   ", "Untitled"))
    }

    @Test
    fun `bullet formatting in the body is preserved verbatim`() {
        // Notes store their bullet glyphs literally in `content` (bulletSequence), so sharing must
        // pass them through unchanged — including nested-level indentation.
        val body = "• First\n  ◦ Nested\n    ▪ Deeper"
        assertEquals(
            "List\n\n$body",
            buildNoteShareText("List", body, "Untitled")
        )
    }
}
