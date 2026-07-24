package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Issue #79 — resuming a paused subscription skips the paused months instead of back-filling. */
class SubscriptionScheduleTest {

    private val today = LocalDate.of(2026, 7, 23)

    @Test
    fun `a renewal months in the past rolls forward past today`() {
        // Paused since April: resuming should charge in August, not back-fill Apr/May/Jun/Jul.
        assertEquals(LocalDate.of(2026, 8, 5), nextRenewalOnOrAfter(LocalDate.of(2026, 4, 5), today))
    }

    @Test
    fun `todays renewal is kept`() {
        assertEquals(today, nextRenewalOnOrAfter(today, today))
    }

    @Test
    fun `a future renewal is untouched`() {
        val future = LocalDate.of(2026, 9, 1)
        assertEquals(future, nextRenewalOnOrAfter(future, today))
    }

    @Test
    fun `end-of-month days clamp the same way plusMonths does`() {
        // Jan 31 -> Feb 28 -> Mar 28 …, matching the existing catch-up loop's arithmetic.
        assertEquals(LocalDate.of(2026, 7, 28), nextRenewalOnOrAfter(LocalDate.of(2026, 1, 31), today))
    }
}
