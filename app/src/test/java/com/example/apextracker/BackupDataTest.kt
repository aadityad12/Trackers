package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Issue #121 — full-dataset backup JSON must round-trip every entity, incl. java.time fields. */
class BackupDataTest {

    @Test
    fun `round-trips entities with date, datetime and time fields`() {
        val data = BackupData(
            exportedAt = "2026-07-24T10:00:00",
            budgetItems = listOf(BudgetItem(id = 1, title = "Coffee", amount = 4.5, date = LocalDate.of(2026, 7, 24), categoryId = 2)),
            categories = listOf(Category(id = 2, name = "Food", colorHex = "#00ff00", monthlyLimit = 100.0)),
            reminders = listOf(
                Reminder(
                    id = 3, name = "Dentist", date = LocalDate.of(2026, 8, 1),
                    time = LocalTime.of(9, 30), priority = ReminderPriority.HIGH.name,
                    recurrence = Recurrence(frequency = RecurrenceFrequency.WEEKLY, endType = RecurrenceEndType.NEVER)
                )
            ),
            notes = listOf(Note(id = 4, title = "n", content = "c", createdAt = LocalDateTime.of(2026, 7, 1, 8, 0), modifiedAt = LocalDateTime.of(2026, 7, 2, 9, 0))),
            studySessions = listOf(StudySession(date = LocalDate.of(2026, 7, 20), subject = "Maths", durationSeconds = 3600)),
            goalCompletions = listOf(GoalCompletion(goalCloudId = "g1", date = LocalDate.of(2026, 7, 23), done = true))
        )

        val restored = parseBackupJson(buildBackupJson(data))!!

        assertEquals(data.budgetItems, restored.budgetItems)
        assertEquals(data.categories, restored.categories)
        assertEquals(data.reminders, restored.reminders)          // exercises LocalTime + Recurrence
        assertEquals(data.notes, restored.notes)                  // exercises LocalDateTime
        assertEquals(data.studySessions, restored.studySessions)
        assertEquals(data.goalCompletions, restored.goalCompletions)
    }

    @Test
    fun `empty backup round-trips`() {
        val restored = parseBackupJson(buildBackupJson(BackupData(exportedAt = "x")))!!
        assertTrue(restored.budgetItems.isEmpty())
        assertEquals(1, restored.formatVersion)
    }

    @Test
    fun `malformed json is rejected, not silently accepted`() {
        var threw = false
        try { parseBackupJson("{ this is not json") } catch (e: Exception) { threw = true }
        assertTrue(threw)
    }

    @Test
    fun `the literal null parses to null for the caller to handle`() {
        assertNull(parseBackupJson("null"))
    }
}
