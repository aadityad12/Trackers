package com.example.apextracker

import java.time.LocalDate

/**
 * Pure scoring for the Dashboard heatmap — no Android, no Room, no coroutines — so every rule is
 * unit-tested in DashboardScoringTest. The ViewModel supplies data pulled from the existing DAOs;
 * everything here is a deterministic function of that data (matches CategoryLimits / BudgetTrends).
 */

/** A single day's tracker totals, the inputs an AUTO goal is evaluated against. */
data class DayMetrics(
    /** subject -> seconds studied that day; empty string is the "No subject" bucket. */
    val studyBySubject: Map<String, Long> = emptyMap(),
    val screenMillis: Long = 0L,
    val spend: Double = 0.0
)

private const val MILLIS_PER_HOUR = 3_600_000.0
private const val SECONDS_PER_HOUR = 3_600.0

/**
 * Goals active on [date]: started on or before it, and not yet archived as of it. [Goal.archivedDate]
 * is the first day the goal stops counting, so the day it was archived is already excluded.
 */
fun activeGoalsOn(date: LocalDate, goals: List<Goal>): List<Goal> =
    goals.filter { goal ->
        !goal.startDate.isAfter(date) &&
            (goal.archivedDate == null || date.isBefore(goal.archivedDate))
    }

/**
 * Whether an AUTO goal's rule holds for a day's metrics. Thresholds are hours for time metrics and
 * currency units for spend. OVER is met at-or-above the threshold, UNDER at-or-below (reaching the
 * target counts). A malformed goal (missing metric/comparator/threshold) is never satisfied.
 */
fun evaluateAutoGoal(goal: Goal, metrics: DayMetrics): Boolean {
    val threshold = goal.threshold ?: return false
    val value: Double = when (goal.metric) {
        GoalMetric.SCREEN_TIME -> metrics.screenMillis / MILLIS_PER_HOUR
        GoalMetric.STUDY -> {
            val seconds = if (goal.subject == null) metrics.studyBySubject.values.sum()
            else metrics.studyBySubject[goal.subject] ?: 0L
            seconds / SECONDS_PER_HOUR
        }
        GoalMetric.SPEND -> metrics.spend
        else -> return false
    }
    return when (goal.comparator) {
        GoalComparator.OVER -> value >= threshold
        GoalComparator.UNDER -> value <= threshold
        else -> false
    }
}

/** Whether [goal] counts as done on [date] — a stored check-off for MANUAL, a live evaluation for AUTO. */
fun isGoalSatisfied(
    goal: Goal,
    date: LocalDate,
    completions: List<GoalCompletion>,
    metrics: DayMetrics
): Boolean = when (goal.type) {
    GoalType.MANUAL ->
        completions.any { it.goalCloudId == goal.cloudId && it.date == date && it.done }
    GoalType.AUTO -> evaluateAutoGoal(goal, metrics)
    else -> false
}

/**
 * Share of that day's active goals completed, or null when no goals were active (renders as an empty
 * cell, distinct from a 0.0 "logged nothing" day). Denominator is the goals active on [date] only,
 * so history is stable as goals are added/archived.
 */
fun dayFraction(
    date: LocalDate,
    goals: List<Goal>,
    completions: List<GoalCompletion>,
    metrics: DayMetrics
): Double? {
    val active = activeGoalsOn(date, goals)
    if (active.isEmpty()) return null
    val satisfied = active.count { isGoalSatisfied(it, date, completions, metrics) }
    return satisfied.toDouble() / active.size
}

/**
 * Heatmap intensity 0..4 for a non-null [fraction]. Only a perfect day (all active goals) reaches 4;
 * 0 is a fully-missed day; 1..3 split the partial range. Callers map a null fraction to an empty cell.
 */
fun intensityBucket(fraction: Double): Int = when {
    fraction >= 1.0 -> 4
    fraction <= 0.0 -> 0
    else -> 1 + (fraction * 3).toInt().coerceIn(0, 2) // 1..3
}

/**
 * Consecutive perfect days (fraction == 1.0) walking back from [endDate] inclusive. Stops at the
 * first day that is not perfect — a day with no active goals breaks the run rather than extending it.
 * [metricsFor] returns the tracker totals for any date the walk visits.
 */
fun perfectDayStreak(
    endDate: LocalDate,
    goals: List<Goal>,
    completions: List<GoalCompletion>,
    metricsFor: (LocalDate) -> DayMetrics
): Int {
    var streak = 0
    var cursor = endDate
    while (true) {
        val fraction = dayFraction(cursor, goals, completions, metricsFor(cursor))
        if (fraction != null && fraction >= 1.0) {
            streak++
            cursor = cursor.minusDays(1)
        } else {
            break
        }
    }
    return streak
}

/**
 * Consecutive days [goal] was satisfied, walking back from [endDate] inclusive. Stops at the first
 * day it was unmet or inactive (before its start date the streak cannot extend).
 */
fun goalStreak(
    goal: Goal,
    endDate: LocalDate,
    completions: List<GoalCompletion>,
    metricsFor: (LocalDate) -> DayMetrics
): Int {
    var streak = 0
    var cursor = endDate
    while (!goal.startDate.isAfter(cursor)) {
        val active = goal.archivedDate == null || cursor.isBefore(goal.archivedDate)
        if (active && isGoalSatisfied(goal, cursor, completions, metricsFor(cursor))) {
            streak++
            cursor = cursor.minusDays(1)
        } else {
            break
        }
    }
    return streak
}
