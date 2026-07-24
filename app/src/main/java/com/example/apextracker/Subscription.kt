package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val amount: Double,
    val renewalDate: LocalDate,
    val notes: String? = null,
    val lastAddedDate: LocalDate? = null,
    /**
     * Paused subscriptions stop auto-generating BudgetItems (Issue #79) without losing their
     * name/notes/history. Resuming rolls `renewalDate` forward past the skipped periods rather
     * than back-filling them — see BudgetViewModel.setSubscriptionPaused.
     */
    val isPaused: Boolean = false,
    val cloudId: String = "",
    val modifiedAt: Long = 0L
)
