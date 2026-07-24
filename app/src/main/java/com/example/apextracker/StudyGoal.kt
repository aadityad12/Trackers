package com.example.apextracker

import java.time.LocalDate

/**
 * Pure study-goal/streak/chart logic (Issue #42), no Android/Room, unit-tested in StudyGoalTest.
 * All operate on the daily grand total across subjects (the goal is minutes-per-day, not
 * per-subject).
 */

/** Total study seconds per day across all subjects, keyed by date. */
fun dailyTotalSecondsByDate(sessions: List<StudySession>): Map<LocalDate, Long> =
    sessions.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.durationSeconds } }

/**
 * Consecutive days meeting [goalSeconds], counting back from [today]. Today counts if it already
 * meets the goal, but not yet reaching it today does NOT break a streak that runs through yesterday
 * — so a streak is "yesterday-inclusive" until today is earned. A goal of 0 (off) has no streak.
 */
fun computeStudyStreak(sessions: List<StudySession>, goalSeconds: Long, today: LocalDate): Int {
    if (goalSeconds <= 0) return 0
    val totals = dailyTotalSecondsByDate(sessions)
    fun met(date: LocalDate) = (totals[date] ?: 0L) >= goalSeconds

    // Start at today if earned, else yesterday — reaching today is a bonus, missing it (so far) isn't a break.
    var cursor = if (met(today)) today else today.minusDays(1)
    var streak = 0
    while (met(cursor)) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** Per-day study minutes for the [days] days ending at [today] (inclusive), oldest first. */
fun weeklyStudyMinutes(sessions: List<StudySession>, days: Int, today: LocalDate): List<Pair<LocalDate, Int>> {
    val totals = dailyTotalSecondsByDate(sessions)
    return (days - 1 downTo 0).map { daysAgo ->
        val day = today.minusDays(daysAgo.toLong())
        day to ((totals[day] ?: 0L) / 60L).toInt()
    }
}

/** Fraction of the daily goal met by [todaySeconds], clamped 0..1. Zero when the goal is off. */
fun goalFraction(todaySeconds: Long, goalSeconds: Long): Float =
    if (goalSeconds <= 0) 0f else (todaySeconds.toDouble() / goalSeconds).coerceIn(0.0, 1.0).toFloat()
