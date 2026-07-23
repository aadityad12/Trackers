package com.example.apextracker

import java.util.Locale

/** Formats a duration as "Xh Ym" (or just "Ym" under an hour). Shared by Study, Screen Time, and Overview. */
fun formatDurationCompact(millis: Long): String {
    val totalMinutes = millis / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%dh %dm", hours, minutes)
    } else {
        String.format(Locale.getDefault(), "%dm", minutes)
    }
}

/**
 * Labels for a duration chart's y-axis gridlines (max, half, zero — top to bottom). Picks one unit
 * for all three from the magnitude of [maxMillis], so a chart whose busiest day is under a minute
 * doesn't render three identical "0m" labels (Issue #97). Values round rather than truncate, so
 * the mid gridline reads as the real halfway point.
 */
fun durationAxisLabels(maxMillis: Long): List<String> {
    val safeMax = maxMillis.coerceAtLeast(0L)
    val values = listOf(safeMax, safeMax / 2, 0L)
    return when {
        safeMax >= 3_600_000L -> values.map { formatDurationCompact(it) }
        safeMax >= 60_000L -> values.map { String.format(Locale.getDefault(), "%dm", Math.round(it / 60_000.0)) }
        else -> values.map { String.format(Locale.getDefault(), "%ds", Math.round(it / 1_000.0)) }
    }
}
