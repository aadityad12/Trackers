package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorHex: String,
    /**
     * Monthly spending cap, or null for "no cap". Nullable rather than 0.0-as-sentinel so
     * "unset" and "capped" stay distinguishable; a non-positive value is normalized to null
     * on the way in (see [effectiveMonthlyLimit]) since it has no renderable progress.
     */
    val monthlyLimit: Double? = null,
    val cloudId: String = "",
    val modifiedAt: Long = 0L
)
