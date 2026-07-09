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
