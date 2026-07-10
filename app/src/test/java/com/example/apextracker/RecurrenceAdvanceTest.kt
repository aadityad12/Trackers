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
