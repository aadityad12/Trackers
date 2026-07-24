package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A per-app daily screen-time budget (Issue #124). Distinct from the aggregate Dashboard
 * SCREEN_TIME goal, which only checks a device-wide total. Local-only (not synced): the limit is a
 * per-device preference and [lastNotifiedDate] is inherently device-specific, so syncing it would
 * only produce duplicate cross-device alerts.
 *
 * [lastNotifiedDate] (ISO yyyy-MM-dd) is the day we last posted the "over your limit" notification
 * for this app, so the 30s polling loop alerts once per day rather than every tick once crossed.
 */
@Entity(tableName = "app_usage_limits")
data class AppUsageLimit(
    @PrimaryKey
    val packageName: String,
    val dailyLimitMinutes: Int,
    val lastNotifiedDate: String? = null
)
