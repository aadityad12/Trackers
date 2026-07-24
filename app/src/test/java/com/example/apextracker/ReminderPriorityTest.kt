package com.example.apextracker

import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #126 — reminder importance parsing, ordering weight, and notification mapping. */
class ReminderPriorityTest {

    @Test
    fun `known names parse, case-insensitively`() {
        assertEquals(ReminderPriority.HIGH, parseReminderPriority("HIGH"))
        assertEquals(ReminderPriority.LOW, parseReminderPriority("low"))
        assertEquals(ReminderPriority.NORMAL, parseReminderPriority(" Normal "))
    }

    @Test
    fun `legacy and malformed values fall back to NORMAL`() {
        // Pre-v16 rows and pre-#126 cloud docs have no value at all.
        assertEquals(ReminderPriority.NORMAL, parseReminderPriority(null))
        assertEquals(ReminderPriority.NORMAL, parseReminderPriority(""))
        assertEquals(ReminderPriority.NORMAL, parseReminderPriority("URGENT"))
    }

    @Test
    fun `sort weight puts high first and low last`() {
        val ordered = ReminderPriority.entries.sortedBy { it.sortWeight() }
        assertEquals(listOf(ReminderPriority.HIGH, ReminderPriority.NORMAL, ReminderPriority.LOW), ordered)
    }

    @Test
    fun `notification priority tracks importance`() {
        assertEquals(NotificationCompat.PRIORITY_HIGH, notificationPriorityFor(ReminderPriority.HIGH))
        assertEquals(NotificationCompat.PRIORITY_DEFAULT, notificationPriorityFor(ReminderPriority.NORMAL))
        assertEquals(NotificationCompat.PRIORITY_LOW, notificationPriorityFor(ReminderPriority.LOW))
    }

    @Test
    fun `a new reminder defaults to NORMAL`() {
        val reminder = Reminder(name = "x", date = java.time.LocalDate.of(2026, 7, 23))
        assertEquals(ReminderPriority.NORMAL, parseReminderPriority(reminder.priority))
    }
}
