package com.example.apextracker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * A read-only snapshot of the goal data the home-screen widgets need (Issues #130/#131), computed
 * off Room in one place so the widgets and the Dashboard agree on the numbers. Pure scoring lives
 * in DashboardScoring.kt; this just wires the DAOs into it.
 */
data class DashboardSnapshot(
    val perfectStreak: Int,
    val today: List<GoalStatus>
)

/** Builds today's per-day metric lookup from the tracker tables — mirrors DashboardViewModel. */
private fun metricsProvider(
    study: List<StudySession>,
    screen: List<ScreenTimeSession>,
    budget: List<BudgetItem>
): (LocalDate) -> DayMetrics {
    val studyByDate = study.groupBy { it.date }
        .mapValues { (_, rows) -> rows.associate { it.subject to it.durationSeconds } }
    val screenByDate = screen.associate { it.date to it.durationMillis }
    val spendByDate = budget.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.amount } }
    return { d ->
        DayMetrics(
            studyBySubject = studyByDate[d] ?: emptyMap(),
            screenMillis = screenByDate[d] ?: 0L,
            spend = spendByDate[d] ?: 0.0
        )
    }
}

/** Reads the current snapshot for [today]; safe to call from a widget's provideGlance. */
suspend fun loadDashboardSnapshot(db: AppDatabase, today: LocalDate): DashboardSnapshot = withContext(Dispatchers.IO) {
    val goals = db.goalDao().getAllGoalsOneShot()
    val completions = db.goalCompletionDao().getAllCompletionsOneShot()
    val metricsFor = metricsProvider(
        db.studySessionDao().getAllSessionsOneShot(),
        db.screenTimeSessionDao().getAllSessionsOneShot(),
        db.budgetDao().getAllItemsOneShot()
    )
    val active = activeGoalsOn(today, goals).sortedWith(compareBy({ it.sortOrder }, { it.id }))
    DashboardSnapshot(
        perfectStreak = perfectDayStreak(today, goals, completions, metricsFor),
        today = active.map { GoalStatus(it, isGoalSatisfied(it, today, completions, metricsFor(today))) }
    )
}
