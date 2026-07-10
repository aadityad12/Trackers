package com.example.apextracker

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ConvertersSafeParsingTest {
    private val gson = Gson()

    @Test
    fun `valid values round-trip`() {
        assertEquals(LocalDate.of(2026, 7, 10), parseLocalDateSafe("2026-07-10"))
        assertEquals(LocalTime.of(9, 30), parseLocalTimeSafe("09:30"))
        assertEquals(LocalDateTime.of(2026, 7, 10, 9, 30), parseLocalDateTimeSafe("2026-07-10T09:30"))
    }

    @Test
    fun `malformed date strings return null instead of throwing`() {
        assertNull(parseLocalDateSafe("not-a-date"))
        assertNull(parseLocalDateSafe("2026-13-45"))
        assertNull(parseLocalDateSafe(""))
        assertNull(parseLocalTimeSafe("25:99"))
        assertNull(parseLocalDateTimeSafe("2026-07-10 09:30")) // space instead of T
    }

    @Test
    fun `valid recurrence JSON parses`() {
        val original = Recurrence(
            frequency = RecurrenceFrequency.MONTHLY,
            endType = RecurrenceEndType.NEVER,
            anchorDay = 31
        )
        val parsed = parseRecurrenceSafe(gson, gson.toJson(original))
        assertEquals(original, parsed)
    }

    @Test
    fun `malformed recurrence JSON returns null instead of throwing`() {
        assertNull(parseRecurrenceSafe(gson, "{truncated"))
        assertNull(parseRecurrenceSafe(gson, "null"))
        assertNull(parseRecurrenceSafe(gson, ""))
    }

    @Test
    fun `recurrence JSON missing required enums is rejected`() {
        // Gson bypasses Kotlin null-safety: these decode "successfully" with null frequency/endType
        assertNull(parseRecurrenceSafe(gson, "{}"))
        assertNull(parseRecurrenceSafe(gson, """{"frequency":"DAILY"}"""))
        assertNull(parseRecurrenceSafe(gson, """{"endType":"NEVER"}"""))
        // Unknown enum constants decode to null in Gson as well
        assertNull(parseRecurrenceSafe(gson, """{"frequency":"HOURLY","endType":"NEVER"}"""))
    }
}
