package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceAdvanceTest {

    private fun monthly(anchorDay: Int? = null) = Recurrence(
        frequency = RecurrenceFrequency.MONTHLY,
        endType = RecurrenceEndType.NEVER,
        anchorDay = anchorDay
    )

    private fun yearly(anchorDay: Int? = null) = Recurrence(
        frequency = RecurrenceFrequency.YEARLY,
        endType = RecurrenceEndType.NEVER,
        anchorDay = anchorDay
    )

    @Test
    fun `monthly recovers original day after a short month`() {
        val recurrence = monthly().withAnchorFrom(LocalDate.of(2026, 1, 31))
        val feb = calculateNextDate(LocalDate.of(2026, 1, 31), recurrence)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        val mar = calculateNextDate(feb!!, recurrence)
        assertEquals(LocalDate.of(2026, 3, 31), mar) // recovers, no drift
        val apr = calculateNextDate(mar!!, recurrence)
        assertEquals(LocalDate.of(2026, 4, 30), apr) // clamped again for 30-day month
        val may = calculateNextDate(apr!!, recurrence)
        assertEquals(LocalDate.of(2026, 5, 31), may)
    }

    @Test
    fun `monthly without anchor falls back to current day (legacy data)`() {
        // Pre-anchor persisted chains keep old behavior until re-anchored
        val next = calculateNextDate(LocalDate.of(2026, 2, 28), monthly())
        assertEquals(LocalDate.of(2026, 3, 28), next)
    }

    @Test
    fun `yearly Feb 29 recovers in the next leap year`() {
        val recurrence = yearly().withAnchorFrom(LocalDate.of(2028, 2, 29))
        val y2029 = calculateNextDate(LocalDate.of(2028, 2, 29), recurrence)
        assertEquals(LocalDate.of(2029, 2, 28), y2029)
        val y2030 = calculateNextDate(y2029!!, recurrence)
        assertEquals(LocalDate.of(2030, 2, 28), y2030)
        val y2031 = calculateNextDate(y2030!!, recurrence)
        assertEquals(LocalDate.of(2031, 2, 28), y2031)
        val y2032 = calculateNextDate(y2031!!, recurrence)
        assertEquals(LocalDate.of(2032, 2, 29), y2032) // leap year recovers the 29th
    }

    @Test
    fun `withAnchorFrom only applies to monthly and yearly and never overwrites`() {
        val daily = Recurrence(frequency = RecurrenceFrequency.DAILY, endType = RecurrenceEndType.NEVER)
        assertNull(daily.withAnchorFrom(LocalDate.of(2026, 1, 31)).anchorDay)

        val anchored = monthly(anchorDay = 15)
        assertEquals(15, anchored.withAnchorFrom(LocalDate.of(2026, 1, 31)).anchorDay)

        assertEquals(31, monthly().withAnchorFrom(LocalDate.of(2026, 1, 31)).anchorDay)
    }

    @Test
    fun `daily and weekly advance plainly`() {
        val daily = Recurrence(frequency = RecurrenceFrequency.DAILY, endType = RecurrenceEndType.NEVER)
        assertEquals(LocalDate.of(2026, 3, 1), calculateNextDate(LocalDate.of(2026, 2, 28), daily))

        val weekly = Recurrence(frequency = RecurrenceFrequency.WEEKLY, endType = RecurrenceEndType.NEVER)
        assertEquals(LocalDate.of(2026, 7, 17), calculateNextDate(LocalDate.of(2026, 7, 10), weekly))
    }

    @Test
    fun `custom finds next matching weekday`() {
        val custom = Recurrence(
            frequency = RecurrenceFrequency.CUSTOM,
            customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            endType = RecurrenceEndType.NEVER
        )
        // 2026-07-10 is a Friday → next is Monday 07-13
        assertEquals(LocalDate.of(2026, 7, 13), calculateNextDate(LocalDate.of(2026, 7, 10), custom))
        // From Monday → Thursday
        assertEquals(LocalDate.of(2026, 7, 16), calculateNextDate(LocalDate.of(2026, 7, 13), custom))
    }

    // ── calculateNextOccurrenceAfter (catch-up for missed recurring reminders, Issue #30) ──

    @Test
    fun `daily missed by 3 days catches up to tomorrow`() {
        val daily = Recurrence(frequency = RecurrenceFrequency.DAILY, endType = RecurrenceEndType.NEVER)
        val today = LocalDate.of(2026, 7, 10)
        val next = calculateNextOccurrenceAfter(LocalDate.of(2026, 7, 7), daily, today)
        assertEquals(LocalDate.of(2026, 7, 11), next)
    }

    @Test
    fun `weekly missed by 2 weeks stays on the same weekday`() {
        val weekly = Recurrence(frequency = RecurrenceFrequency.WEEKLY, endType = RecurrenceEndType.NEVER)
        // Chain runs on Mondays; missed 2026-06-22 and 06-29; completed Friday 2026-07-10
        val next = calculateNextOccurrenceAfter(LocalDate.of(2026, 6, 22), weekly, LocalDate.of(2026, 7, 10))
        assertEquals(LocalDate.of(2026, 7, 13), next)
        assertEquals(DayOfWeek.MONDAY, next!!.dayOfWeek)
    }

    @Test
    fun `on-time completion advances one plain period`() {
        val daily = Recurrence(frequency = RecurrenceFrequency.DAILY, endType = RecurrenceEndType.NEVER)
        val today = LocalDate.of(2026, 7, 10)
        assertEquals(LocalDate.of(2026, 7, 11), calculateNextOccurrenceAfter(today, daily, today))
        // Completing a future-dated reminder early keeps its own grid too
        assertEquals(LocalDate.of(2026, 7, 13), calculateNextOccurrenceAfter(LocalDate.of(2026, 7, 12), daily, today))
    }

    @Test
    fun `monthly catch-up respects the anchor day`() {
        val recurrence = monthly().withAnchorFrom(LocalDate.of(2026, 1, 31))
        // Missed Jan 31 through May; completed 2026-06-10 → next is Jun 30 (clamped), not Jul
        val next = calculateNextOccurrenceAfter(LocalDate.of(2026, 1, 31), recurrence, LocalDate.of(2026, 6, 10))
        assertEquals(LocalDate.of(2026, 6, 30), next)
    }

    @Test
    fun `catch-up returns a date past UNTIL_DATE end so caller can stop the chain`() {
        // The end-condition check lives in the caller; verify the catch-up date lands after the
        // window when the whole missed stretch overshoots the end date.
        val untilEnd = Recurrence(
            frequency = RecurrenceFrequency.DAILY,
            endType = RecurrenceEndType.UNTIL_DATE,
            endDate = LocalDate.of(2026, 7, 5)
        )
        val next = calculateNextOccurrenceAfter(LocalDate.of(2026, 7, 1), untilEnd, LocalDate.of(2026, 7, 10))
        assertEquals(LocalDate.of(2026, 7, 11), next)
        // Caller's UNTIL_DATE guard: next.isAfter(endDate) → chain ends
        assertEquals(true, next!!.isAfter(untilEnd.endDate))
    }

    @Test
    fun `catch-up with unadvanceable custom recurrence returns null`() {
        val empty = Recurrence(
            frequency = RecurrenceFrequency.CUSTOM,
            customDays = emptySet(),
            endType = RecurrenceEndType.NEVER
        )
        assertNull(calculateNextOccurrenceAfter(LocalDate.of(2026, 7, 1), empty, LocalDate.of(2026, 7, 10)))
    }

    @Test
    fun `custom with no days returns null`() {
        val empty = Recurrence(
            frequency = RecurrenceFrequency.CUSTOM,
            customDays = emptySet(),
            endType = RecurrenceEndType.NEVER
        )
        assertNull(calculateNextDate(LocalDate.of(2026, 7, 10), empty))
        val absent = Recurrence(frequency = RecurrenceFrequency.CUSTOM, endType = RecurrenceEndType.NEVER)
        assertNull(calculateNextDate(LocalDate.of(2026, 7, 10), absent))
    }
}
