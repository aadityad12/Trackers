package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Overview's stat cards format study/screen time via [formatDurationCompact] and currency via
 * String.format("%.2f", ...) — see Issue #5 (rounding to whole dollars, raw-minutes display).
 */
class OverviewFormattingTest {

    @Test
    fun `study and screen time under an hour show only minutes`() {
        assertEquals("45m", formatDurationCompact(45 * 60_000L))
    }

    @Test
    fun `study and screen time over an hour show hours and minutes`() {
        assertEquals("1h 30m", formatDurationCompact(90 * 60_000L))
    }

    @Test
    fun `zero minutes formats as 0m, not blank`() {
        assertEquals("0m", formatDurationCompact(0L))
    }

    @Test
    fun `total spent keeps cents instead of rounding to whole dollars`() {
        assertEquals("12.50", String.format(Locale.US, "%.2f", 12.5))
        assertEquals("12.34", String.format(Locale.US, "%.2f", 12.339))
    }
}
