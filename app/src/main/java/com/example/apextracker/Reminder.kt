package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Reminder importance — drives list ordering and the notification's channel priority. */
enum class ReminderPriority { LOW, NORMAL, HIGH }

/** Parses a stored/synced priority string, falling back to NORMAL for anything unrecognized. */
fun parseReminderPriority(raw: String?): ReminderPriority =
    ReminderPriority.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
        ?: ReminderPriority.NORMAL

/** Sort weight: HIGH first within a day, then NORMAL, then LOW. */
fun ReminderPriority.sortWeight(): Int = when (this) {
    ReminderPriority.HIGH -> 0
    ReminderPriority.NORMAL -> 1
    ReminderPriority.LOW -> 2
}

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val date: LocalDate,
    val time: LocalTime? = null, // null means all day
    val description: String? = null,
    val isCompleted: Boolean = false,
    val recurrence: Recurrence? = null,
    val parentId: Long? = null, // if this is an instance of a recurring reminder
    val occurrencesCompleted: Int = 0,
    val cloudId: String = "",
    val parentCloudId: String? = null,
    val modifiedAt: Long = 0L,
    /**
     * Relative importance (Issue #126). Stored as the enum name; unknown/legacy values read back as
     * [ReminderPriority.NORMAL], so pre-v16 rows and older cloud docs behave exactly as before.
     */
    val priority: String = ReminderPriority.NORMAL.name
) {
    fun isOverdue(now: LocalDateTime): Boolean {
        if (isCompleted) return false
        val reminderDateTime = LocalDateTime.of(date, time ?: LocalTime.MAX)
        return reminderDateTime.isBefore(now)
    }
}
