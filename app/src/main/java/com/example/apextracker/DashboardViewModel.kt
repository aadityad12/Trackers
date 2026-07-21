package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/** One rendered heatmap cell. [fraction] is null when no goals were active that day (empty cell). */
data class DayCell(
    val date: LocalDate,
    val fraction: Double?,
    val bucket: Int // -1 = no active goals, else 0..4 from intensityBucket
)

/** A goal's live status for a given day, for the Today card / day breakdown. */
data class GoalStatus(
    val goal: Goal,
    val satisfied: Boolean
)

data class DashboardUiState(
    val weeks: List<List<DayCell?>>, // row 0 = current week (newest on top); inner list length 7 (Sun..Sat); null = future/padding
    val todayGoals: List<GoalStatus>,
    val activeGoals: List<Goal>,
    val perfectStreak: Int,
    val today: LocalDate,
    val loaded: Boolean,
    // Raw inputs so any day's breakdown (the tap-a-day sheet) can be computed reactively off the
    // same state via dayGoalStatuses(), instead of re-querying Room.
    val allGoals: List<Goal> = emptyList(),
    val completions: List<GoalCompletion> = emptyList(),
    val studyByDate: Map<LocalDate, Map<String, Long>> = emptyMap(),
    val screenByDate: Map<LocalDate, Long> = emptyMap(),
    val spendByDate: Map<LocalDate, Double> = emptyMap()
) {
    companion object {
        val EMPTY = DashboardUiState(emptyList(), emptyList(), emptyList(), 0, LocalDate.now(), loaded = false)
    }
}

/** The tracker metrics for [date], pulled from a state snapshot's pre-indexed per-day maps. */
fun DashboardUiState.metricsFor(date: LocalDate): DayMetrics = DayMetrics(
    studyBySubject = studyByDate[date] ?: emptyMap(),
    screenMillis = screenByDate[date] ?: 0L,
    spend = spendByDate[date] ?: 0.0
)

/** Live status of every goal active on [date] — powers the day-detail sheet (today or any past day). */
fun DashboardUiState.dayGoalStatuses(date: LocalDate): List<GoalStatus> {
    val metrics = metricsFor(date)
    return activeGoalsOn(date, allGoals)
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .map { GoalStatus(it, isGoalSatisfied(it, date, completions, metrics)) }
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val goalDao = db.goalDao()
    private val completionDao = db.goalCompletionDao()
    private val studyDao = db.studySessionDao()
    private val screenDao = db.screenTimeSessionDao()
    private val budgetDao = db.budgetDao()

    val uiState: StateFlow<DashboardUiState> = combine(
        goalDao.getAllGoals(),
        completionDao.getAllCompletions(),
        studyDao.getAllSessions(),
        screenDao.getAllSessions(),
        budgetDao.getAllItems()
    ) { goals, completions, study, screen, budget ->
        buildState(goals, completions, study, screen, budget)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState.EMPTY)

    private fun buildState(
        goals: List<Goal>,
        completions: List<GoalCompletion>,
        study: List<StudySession>,
        screen: List<ScreenTimeSession>,
        budget: List<BudgetItem>
    ): DashboardUiState {
        val today = LocalDate.now()

        // Pre-index existing per-day totals so metricsFor is a cheap lookup, not a rescan.
        val studyByDate: Map<LocalDate, Map<String, Long>> =
            study.groupBy { it.date }.mapValues { (_, rows) -> rows.associate { it.subject to it.durationSeconds } }
        val screenByDate: Map<LocalDate, Long> = screen.associate { it.date to it.durationMillis }
        val spendByDate: Map<LocalDate, Double> =
            budget.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.amount } }

        val metricsFor: (LocalDate) -> DayMetrics = { d ->
            DayMetrics(
                studyBySubject = studyByDate[d] ?: emptyMap(),
                screenMillis = screenByDate[d] ?: 0L,
                spend = spendByDate[d] ?: 0.0
            )
        }

        // Grid spans from the earliest goal's week down to today, clamped so a brand-new user still
        // sees a proper graph and a long-running one doesn't render an unbounded history.
        val topSunday = weekSunday(today)
        val earliestStart = goals.minOfOrNull { it.startDate } ?: today
        val spanWeeks = ChronoUnit.WEEKS.between(weekSunday(earliestStart), topSunday).toInt() + 1
        val weekCount = spanWeeks.coerceIn(MIN_WEEKS, MAX_WEEKS)

        val weeks = (0 until weekCount).map { r ->
            val sunday = topSunday.minusWeeks(r.toLong())
            (0 until 7).map { c ->
                val date = sunday.plusDays(c.toLong())
                if (date.isAfter(today)) {
                    null
                } else {
                    val fraction = dayFraction(date, goals, completions, metricsFor(date))
                    DayCell(date, fraction, fraction?.let { intensityBucket(it) } ?: -1)
                }
            }
        }

        val active = activeGoalsOn(today, goals).sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val todayGoals = active.map { GoalStatus(it, isGoalSatisfied(it, today, completions, metricsFor(today))) }
        val streak = perfectDayStreak(today, goals, completions, metricsFor)

        return DashboardUiState(
            weeks = weeks,
            todayGoals = todayGoals,
            activeGoals = active,
            perfectStreak = streak,
            today = today,
            loaded = true,
            allGoals = goals,
            completions = completions,
            studyByDate = studyByDate,
            screenByDate = screenByDate,
            spendByDate = spendByDate
        )
    }

    /** Toggle today's completion for a MANUAL goal (AUTO goals are computed, never toggled). */
    fun toggleTodayGoal(goal: Goal) = toggleGoalForDate(goal, LocalDate.now())

    /**
     * Toggle a MANUAL goal's completion for an arbitrary [date] — the tap-a-day backfill path.
     * No-ops for AUTO goals, cloudId-less goals, or dates the goal wasn't active on.
     */
    fun toggleGoalForDate(goal: Goal, date: LocalDate) {
        if (goal.type != GoalType.MANUAL || goal.cloudId.isEmpty()) return
        if (goal.startDate.isAfter(date)) return
        if (goal.archivedDate != null && !date.isBefore(goal.archivedDate)) return
        viewModelScope.launch {
            val existing = completionDao.getByGoalAndDate(goal.cloudId, date)
            completionDao.upsert(
                GoalCompletion(
                    goalCloudId = goal.cloudId,
                    date = date,
                    done = !(existing?.done ?: false),
                    modifiedAt = System.currentTimeMillis()
                )
            )
            // Cloud push is wired in Phase 5 (FirebaseManager goal-completion sync).
        }
    }

    fun addGoal(
        name: String,
        type: String,
        metric: String?,
        comparator: String?,
        threshold: Double?,
        subject: String?
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            goalDao.insertGoal(
                Goal(
                    name = trimmed,
                    type = type,
                    metric = metric,
                    comparator = comparator,
                    threshold = threshold,
                    subject = subject?.trim()?.takeIf { it.isNotEmpty() },
                    startDate = LocalDate.now(),
                    cloudId = UUID.randomUUID().toString(),
                    modifiedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Edit an existing goal's definition. Preserves id/startDate/cloudId; bumps modifiedAt. */
    fun updateGoal(
        goal: Goal,
        name: String,
        type: String,
        metric: String?,
        comparator: String?,
        threshold: Double?,
        subject: String?
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            goalDao.updateGoal(
                goal.copy(
                    name = trimmed,
                    type = type,
                    metric = metric,
                    comparator = comparator,
                    threshold = threshold,
                    subject = subject?.trim()?.takeIf { it.isNotEmpty() },
                    modifiedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Stop a goal counting from today onward, preserving its history (see [activeGoalsOn]). */
    fun archiveGoal(goal: Goal) {
        viewModelScope.launch {
            goalDao.updateGoal(goal.copy(archivedDate = LocalDate.now(), modifiedAt = System.currentTimeMillis()))
        }
    }

    fun unarchiveGoal(goal: Goal) {
        viewModelScope.launch {
            goalDao.updateGoal(goal.copy(archivedDate = null, modifiedAt = System.currentTimeMillis()))
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            goalDao.deleteGoal(goal)
            // Drop its check-off history too so a re-created cloudId can't inherit stale rows.
            if (goal.cloudId.isNotEmpty()) {
                completionDao.getByGoal(goal.cloudId).forEach { completionDao.delete(it) }
            }
        }
    }

    private fun weekSunday(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value % 7).toLong()) // ISO MON=1..SUN=7; SUN%7=0

    companion object {
        private const val MIN_WEEKS = 13 // ~a quarter, so a new user still sees a real graph
        private const val MAX_WEEKS = 53 // ~a year cap
    }
}
