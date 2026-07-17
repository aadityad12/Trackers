package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // resolveTriggerTime — the "when, if ever" decision (Issue #80).

    @Test
    fun `a trigger still in the future is used as-is`() {
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = LocalTime.of(15, 0))
        val now = LocalDateTime.of(2026, 7, 10, 9, 0)

        val trigger = ReminderScheduler.resolveTriggerTime(reminder, allDayTime, offsetMinutes, now)

        assertEquals(LocalDateTime.of(2026, 7, 10, 14, 30), trigger)
    }

    @Test
    fun `a task nearer than the offset notifies now instead of never`() {
        // Due in 10 minutes with a 30-minute offset: the naive trigger is 20 minutes ago.
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = LocalTime.of(15, 0))
        val now = LocalDateTime.of(2026, 7, 10, 14, 50)

        val trigger = ReminderScheduler.resolveTriggerTime(reminder, allDayTime, offsetMinutes, now)

        assertEquals(now, trigger)
    }

    @Test
    fun `a large offset does not suppress a task that is still upcoming`() {
        // The 100-minute offset that made Issue #80 reproduce on-device.
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = LocalTime.of(15, 0))
        val now = LocalDateTime.of(2026, 7, 10, 14, 59)

        val trigger = ReminderScheduler.resolveTriggerTime(reminder, allDayTime, 100, now)

        assertEquals(now, trigger)
    }

    @Test
    fun `a task whose own due time has passed is not scheduled`() {
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = LocalTime.of(15, 0))
        val now = LocalDateTime.of(2026, 7, 10, 15, 1)

        assertNull(ReminderScheduler.resolveTriggerTime(reminder, allDayTime, offsetMinutes, now))
    }

    @Test
    fun `a task due exactly now is not scheduled`() {
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = LocalTime.of(15, 0))
        val now = LocalDateTime.of(2026, 7, 10, 15, 0)

        assertNull(ReminderScheduler.resolveTriggerTime(reminder, allDayTime, offsetMinutes, now))
    }

    @Test
    fun `an all-day reminder still notifies at its all-day time`() {
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = null)
        val now = LocalDateTime.of(2026, 7, 10, 9, 0)

        val trigger = ReminderScheduler.resolveTriggerTime(reminder, allDayTime, offsetMinutes, now)

        assertEquals(LocalDateTime.of(2026, 7, 10, 12, 0), trigger)
    }

    @Test
    fun `an all-day reminder past its notification time is not clamped`() {
        // All-day reminders take no offset, so a past trigger is the real notification time
        // having been and gone — there is nothing to clamp to, even though the day isn't over.
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = null)
        val now = LocalDateTime.of(2026, 7, 10, 18, 0)

        assertNull(ReminderScheduler.resolveTriggerTime(reminder, allDayTime, offsetMinutes, now))
    }

    @Test
    fun `a zero offset schedules exactly at the task time`() {
        val reminder = Reminder(name = "Test", date = LocalDate.of(2026, 7, 10), time = LocalTime.of(15, 0))
        val now = LocalDateTime.of(2026, 7, 10, 14, 59)

        val trigger = ReminderScheduler.resolveTriggerTime(reminder, allDayTime, 0, now)

        assertEquals(LocalDateTime.of(2026, 7, 10, 15, 0), trigger)
    }
}
