package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "screen_time_sessions")
data class ScreenTimeSession(
    @PrimaryKey
    val date: LocalDate,
    val durationMillis: Long
)
