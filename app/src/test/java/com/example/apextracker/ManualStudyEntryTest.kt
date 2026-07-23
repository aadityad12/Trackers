package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #122 — parsing the manual past-session dialog's hours/minutes fields. */
class ManualStudyEntryTest {

    @Test
    fun `hours and minutes combine`() {
        assertEquals(2 * 3600L + 30 * 60L, parseManualDurationSeconds("2", "30"))
        assertEquals(45 * 60L, parseManualDurationSeconds("0", "45"))
    }

    @Test
    fun `blank fields count as zero`() {
        assertEquals(3600L, parseManualDurationSeconds("1", ""))
        assertEquals(600L, parseManualDurationSeconds("", "10"))
        assertEquals(0L, parseManualDurationSeconds("", ""))
    }

    @Test
    fun `minutes above 59 roll into hours`() {
        assertEquals(90 * 60L, parseManualDurationSeconds("0", "90"))
    }

    @Test
    fun `invalid input is rejected`() {
        assertNull(parseManualDurationSeconds("two", "0"))
        assertNull(parseManualDurationSeconds("1", "-5"))
        assertNull(parseManualDurationSeconds("1.5", "0"))
    }
}
