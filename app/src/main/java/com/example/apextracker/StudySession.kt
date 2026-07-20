package com.example.apextracker

import androidx.room.Entity
import java.time.LocalDate

/**
 * One study total, keyed by (date, subject). A day can now hold several rows — one per subject
 * the user studied — instead of a single aggregate. [subject] is never null; the empty string is
 * the "No subject" / uncategorized bucket, which is exactly what every pre-v14 daily total
 * migrates into, so a user who never picks a subject sees the same single-total behaviour as before.
 */
@Entity(tableName = "study_sessions", primaryKeys = ["date", "subject"])
data class StudySession(
    val date: LocalDate,
    val subject: String = "",
    val durationSeconds: Long
)
