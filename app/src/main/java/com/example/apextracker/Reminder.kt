package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
    val occurrencesCompleted: Int = 0
) {
    fun isOverdue(now: LocalDateTime): Boolean {
        if (isCompleted) return false
        val reminderDateTime = LocalDateTime.of(date, time ?: LocalTime.MAX)
        return reminderDateTime.isBefore(now)
    }
}
