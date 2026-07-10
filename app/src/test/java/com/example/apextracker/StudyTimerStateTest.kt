package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StudyTimerStateTest {
    private val utc = ZoneId.of("UTC")

    private fun millisOf(dateTime: LocalDateTime): Long =
        dateTime.atZone(utc).toInstant().toEpochMilli()

    @Test
    fun `session dying mid-day is credited up to midnight`() {
        val state = PersistedTimerState(
            startedAtMillis = millisOf(LocalDateTime.of(2026, 7, 9, 23, 0)),
            baseSeconds = 600,
            date = LocalDate.of(2026, 7, 9)
        )
        // 23:00 → 00:00 is 3600s, plus the 600s accumulated before the last start
        assertEquals(4200, finalizeSecondsAtEndOfDay(state, utc))
    }

    @Test
    fun `start timestamp past the day boundary contributes nothing extra`() {
        // Defensive: clock skew could persist a start time after the stored day already ended
        val state = PersistedTimerState(
            startedAtMillis = millisOf(LocalDateTime.of(2026, 7, 10, 0, 30)),
            baseSeconds = 120,
            date = LocalDate.of(2026, 7, 9)
        )
        assertEquals(120, finalizeSecondsAtEndOfDay(state, utc))
    }

    @Test
    fun `zero base with full-day run credits the whole day`() {
        val state = PersistedTimerState(
            startedAtMillis = millisOf(LocalDateTime.of(2026, 7, 9, 0, 0)),
            baseSeconds = 0,
            date = LocalDate.of(2026, 7, 9)
        )
        assertEquals(24 * 3600L, finalizeSecondsAtEndOfDay(state, utc))
    }
}
