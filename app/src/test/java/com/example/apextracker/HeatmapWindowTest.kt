package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Issue #128 — the heatmap's rolling-year default and per-year windows. */
class HeatmapWindowTest {

    private val today = LocalDate.of(2026, 7, 23) // a Thursday

    private fun cell(date: LocalDate) = DayCell(date, null, -1)

    @Test
    fun `rolling window is the last 365 days ending today`() {
        val (start, end) = heatmapRange(null, today)
        assertEquals(today, end)
        assertEquals(LocalDate.of(2025, 7, 24), start)
        assertEquals(364, java.time.temporal.ChronoUnit.DAYS.between(start, end))
    }

    @Test
    fun `a past year window is that whole calendar year`() {
        val (start, end) = heatmapRange(2025, today)
        assertEquals(LocalDate.of(2025, 1, 1), start)
        assertEquals(LocalDate.of(2025, 12, 31), end)
    }

    @Test
    fun `the current year window stops at today, never in the future`() {
        val (start, end) = heatmapRange(2026, today)
        assertEquals(LocalDate.of(2026, 1, 1), start)
        assertEquals(today, end)
    }

    @Test
    fun `year buttons run from the earliest goal to this year, newest first`() {
        assertEquals(listOf(2026, 2025, 2024), heatmapYears(LocalDate.of(2024, 5, 1), today))
        // No goals yet: only the current year is offered.
        assertEquals(listOf(2026), heatmapYears(null, today))
    }

    @Test
    fun `weeks are newest-first Sunday rows with padding outside the range`() {
        val start = LocalDate.of(2026, 7, 20) // Monday
        val weeks = heatmapWeeks(start, today) { cell(it) }
        assertEquals(1, weeks.size)
        val row = weeks.first()
        assertEquals(7, row.size)
        assertNull(row[0]) // Sunday the 19th is before the range
        assertEquals(LocalDate.of(2026, 7, 20), row[1]?.date)
        assertEquals(today, row[4]?.date)
        assertNull(row[5]) // Friday is after the range end
    }

    @Test
    fun `a rolling year is at most 54 rows and starts with the newest week`() {
        val (start, end) = heatmapRange(null, today)
        val weeks = heatmapWeeks(start, end) { cell(it) }
        assertTrue(weeks.size in 52..54)
        assertEquals(today, weeks.first().first { it != null }?.let { row -> weeks.first()[4]?.date })
    }

    @Test
    fun `an inverted range yields nothing`() {
        assertTrue(heatmapWeeks(today, today.minusDays(1)) { cell(it) }.isEmpty())
    }
}
