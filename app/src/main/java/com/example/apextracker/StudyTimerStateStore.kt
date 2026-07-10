package com.example.apextracker

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

data class PersistedTimerState(
    val startedAtMillis: Long,
    val baseSeconds: Long,
    val date: LocalDate
)

/**
 * Final duration for a session that was still running when the process died and whose day has
 * since ended: base + elapsed from start to that day's midnight boundary. Time past midnight is
 * deliberately not credited to any day — we can't know when the process actually died.
 */
fun finalizeSecondsAtEndOfDay(state: PersistedTimerState, zone: ZoneId): Long {
    val endOfDayMillis = state.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val ranSeconds = ((endOfDayMillis - state.startedAtMillis) / 1000).coerceAtLeast(0)
    return state.baseSeconds + ranSeconds
}

/**
 * Persists the running stopwatch's identity (start timestamp + accumulated base) so a process
 * death mid-session doesn't silently stop the count. Written on start/pause/reset/rollover —
 * not per tick — so it's cheap; SharedPreferences is sufficient for three fields.
 */
class StudyTimerStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("study_timer_state", Context.MODE_PRIVATE)

    fun saveRunning(startedAtMillis: Long, baseSeconds: Long, date: LocalDate) {
        prefs.edit()
            .putBoolean("is_running", true)
            .putLong("started_at", startedAtMillis)
            .putLong("base_seconds", baseSeconds)
            .putString("date", date.toString())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** State of a timer that was running when the app last died, or null if none/not running. */
    fun loadRunning(): PersistedTimerState? {
        if (!prefs.getBoolean("is_running", false)) return null
        val date = prefs.getString("date", null)?.let { parseLocalDateSafe(it) } ?: return null
        return PersistedTimerState(
            startedAtMillis = prefs.getLong("started_at", 0L),
            baseSeconds = prefs.getLong("base_seconds", 0L),
            date = date
        )
    }
}
