package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "excluded_apps")
data class ExcludedApp(
    @PrimaryKey
    val packageName: String
)
