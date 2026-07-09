package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderSchedulerTest {

    private val allDayTime = LocalTime.of(12, 0)
    private val offsetMinutes = 30

    @Test
    fun `all-day reminder triggers at the configured all-day notification time`() {
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = null)

        val trigger = ReminderScheduler.computeTriggerTime(reminder, allDayTime, offsetMinutes)

        assertEquals(LocalDateTime.of(2026, 7, 10, 12, 0), trigger)
    }

    @Test
    fun `timed reminder triggers the configured offset before its due time`() {
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = LocalTime.of(15, 0))

        val trigger = ReminderScheduler.computeTriggerTime(reminder, allDayTime, offsetMinutes)

        assertEquals(LocalDateTime.of(2026, 7, 10, 14, 30), trigger)
    }

    @Test
    fun `offset can push the trigger time onto the previous day`() {
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = LocalTime.of(0, 10))

        val trigger = ReminderScheduler.computeTriggerTime(reminder, allDayTime, offsetMinutes)

        assertEquals(LocalDateTime.of(2026, 7, 9, 23, 40), trigger)
    }
}
