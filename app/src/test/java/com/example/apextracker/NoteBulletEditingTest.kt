package com.example.apextracker

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteBulletEditingTest {

    private fun backspaceOnce(value: TextFieldValue): TextFieldValue {
        val cursor = value.selection.start
        val newText = value.text.removeRange(cursor - 1, cursor)
        val newValue = TextFieldValue(newText, TextRange(cursor - 1))
        return handleNoteContentChange(newValue, value)
    }

    @Test
    fun `backspacing an empty bullet line clears it in a single keystroke`() {
        val bulleted = TextFieldValue("• ", TextRange(2))

        val afterOneBackspace = backspaceOnce(bulleted)

        assertEquals("", afterOneBackspace.text)
    }

    private fun indentLine(line: String): String {
        val match = bulletRegex.find(line)
        return if (match != null) {
            val currentIndex = bulletSequence.indexOf(match.value)
            if (currentIndex != -1 && currentIndex < bulletSequence.size - 1) {
                bulletSequence[currentIndex + 1] + line.substring(match.value.length)
            } else line
        } else {
            line
        }
    }

    @Test
    fun `indenting a plain non-bulleted line does nothing`() {
        val plain = TextFieldValue("Just some text", TextRange(15))

        val result = modifyCurrentLine(plain) { indentLine(it) }

        assertEquals("Just some text", result.text)
    }

    @Test
    fun `indenting an already-bulleted line promotes it one level`() {
        val bulleted = TextFieldValue("• item", TextRange(7))

        val result = modifyCurrentLine(bulleted) { indentLine(it) }

        assertEquals("  ◦ item", result.text)
    }

    @Test
    fun `pressing bullet button on empty line inserts the marker`() {
        val empty = TextFieldValue("", TextRange(0))

        val result = modifyCurrentLine(empty) { line ->
            val match = bulletRegex.find(line)
            if (match != null && match.value == bulletSequence[0]) {
                line.substring(match.value.length)
            } else if (match != null) {
                bulletSequence[0] + line.substring(match.value.length)
            } else {
                bulletSequence[0] + line
            }
        }

        assertEquals("• ", result.text)
    }
}
