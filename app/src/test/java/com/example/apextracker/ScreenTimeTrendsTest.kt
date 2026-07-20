package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScreenTimeTrendsTest {

    private val today = LocalDate.of(2026, 7, 17)

    private fun session(date: LocalDate, millis: Long) = ScreenTimeSession(date = date, durationMillis = millis)

    @Test
    fun `returns one entry per day in range, oldest first`() {
        val totals = dailyTotals(emptyList(), days = 7, today = today)
        assertEquals(
            (6L downTo 0L).map { today.minusDays(it) },
            totals.map { it.first }
        )
    }

    @Test
    fun `empty input yields all zero days, no gaps`() {
        val totals = dailyTotals(emptyList(), days = 7, today = today)
        assertEquals(7, totals.size)
        assertEquals(List(7) { 0L }, totals.map { it.second })
    }

    @Test
    fun `fewer than 7 days of data fills missing days with zero`() {
        val sessions = listOf(
            session(today, 3_600_000L),
            session(today.minusDays(2), 1_800_000L)
        )
        val totals = dailyTotals(sessions, days = 7, today = today)
        assertEquals(
            listOf(0L, 0L, 0L, 0L, 1_800_000L, 0L, 3_600_000L),
            totals.map { it.second }
        )
    }

    @Test
    fun `exactly 7 days of data are all represented`() {
        val sessions = (0L until 7L).map { session(today.minusDays(it), (it + 1) * 1000L) }
        val totals = dailyTotals(sessions, days = 7, today = today)
        // oldest first: today-6 .. today => (7,6,5,4,3,2,1) * 1000
        assertEquals(
            listOf(7000L, 6000L, 5000L, 4000L, 3000L, 2000L, 1000L),
            totals.map { it.second }
        )
    }

    @Test
    fun `more than 7 days keeps only the last 7`() {
        val sessions = (0L until 30L).map { session(today.minusDays(it), 1000L) }
        val totals = dailyTotals(sessions, days = 7, today = today)
        assertEquals(7, totals.size)
        // every day in the last-7 window has a session
        assertEquals(List(7) { 1000L }, totals.map { it.second })
        // the oldest included day is today-6; anything older is excluded
        assertEquals(today.minusDays(6), totals.first().first)
        assertEquals(today, totals.last().first)
    }

    @Test
    fun `multiple sessions on the same day are summed`() {
        val sessions = listOf(
            session(today, 1000L),
            session(today, 2500L),
            session(today.minusDays(1), 500L)
        )
        val totals = dailyTotals(sessions, days = 7, today = today)
        assertEquals(500L, totals[5].second)
        assertEquals(3500L, totals[6].second)
    }

    @Test
    fun `days outside the window are ignored`() {
        val sessions = listOf(session(today.minusDays(10), 9_999_000L))
        val totals = dailyTotals(sessions, days = 7, today = today)
        assertEquals(List(7) { 0L }, totals.map { it.second })
    }
}
