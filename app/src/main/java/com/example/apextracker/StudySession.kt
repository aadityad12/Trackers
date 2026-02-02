package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey
    val date: LocalDate,
    val durationSeconds: Long
)
