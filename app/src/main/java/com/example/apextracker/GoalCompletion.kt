package com.example.apextracker

import androidx.room.Entity
import java.time.LocalDate

/**
 * One day's manual check-off for a [Goal]. Only MANUAL goals ever get rows here — AUTO goals are
 * computed on read, so storing them would freeze data that should track the underlying totals.
 *
 * Keyed by ([goalCloudId], [date]) rather than the goal's local row id so the Firestore doc id
 * ("{goalCloudId}|{date}") is stable across devices (a goal's local id differs per device).
 */
@Entity(tableName = "goal_completions", primaryKeys = ["goalCloudId", "date"])
data class GoalCompletion(
    val goalCloudId: String,
    val date: LocalDate,
    val done: Boolean,
    val modifiedAt: Long = 0L
)
