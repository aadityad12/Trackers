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
    // The heatmap grid itself is built in the view from the raw inputs below, so switching the
    // visible year (Issue #128) doesn't need a round-trip through the ViewModel.
    val todayGoals: List<GoalStatus>,
    val activeGoals: List<Goal>,
    val perfectStreak: Int,
    val today: LocalDate,
    val loaded: Boolean,
    /** Earliest goal start, for offering per-year heatmap buttons; null when there are no goals. */
    val earliestGoalStart: LocalDate? = null,
    // Raw inputs so any day's breakdown (the tap-a-day sheet) can be computed reactively off the
    // same state via dayGoalStatuses(), instead of re-querying Room.
    val allGoals: List<Goal> = emptyList(),
    val completions: List<GoalCompletion> = emptyList(),
    val studyByDate: Map<LocalDate, Map<String, Long>> = emptyMap(),
    val screenByDate: Map<LocalDate, Long> = emptyMap(),
    val spendByDate: Map<LocalDate, Double> = emptyMap()
) {
    companion object {
        val EMPTY = DashboardUiState(emptyList(), emptyList(), 0, LocalDate.now(), loaded = false)
    }
}

/** The tracker metrics for [date], pulled from a state snapshot's pre-indexed per-day maps. */
fun DashboardUiState.metricsFor(date: LocalDate): DayMetrics = DayMetrics(
    studyBySubject = studyByDate[date] ?: emptyMap(),
    screenMillis = screenByDate[date] ?: 0L,
    spend = spendByDate[date] ?: 0.0
)

/** One heatmap cell for [date], computed from the same snapshot the day sheet uses. */
fun DashboardUiState.dayCell(date: LocalDate): DayCell {
    val fraction = dayFraction(date, allGoals, completions, metricsFor(date))
    return DayCell(date, fraction, fraction?.let { intensityBucket(it) } ?: -1)
}

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
    // Fire-and-forget cloud sync (Room is source of truth); mirrors the other ViewModels.
    private val firebaseManager = FirebaseManager(application)

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

        val active = activeGoalsOn(today, goals).sortedWith(compareBy({ it.sortOrder }, { it.id }))
        val todayGoals = active.map { GoalStatus(it, isGoalSatisfied(it, today, completions, metricsFor(today))) }
        val streak = perfectDayStreak(today, goals, completions, metricsFor)

        return DashboardUiState(
            todayGoals = todayGoals,
            activeGoals = active,
            perfectStreak = streak,
            today = today,
            loaded = true,
            earliestGoalStart = goals.minOfOrNull { it.startDate },
            allGoals = goals,
            completions = completions,
            studyByDate = studyByDate,
            screenByDate = screenByDate,
            spendByDate = spendByDate
        )
    }

    /** (goalCloudId, date) pairs whose toggle is mid-flight — see [toggleGoalForDate]. */
    private val togglesInFlight = mutableSetOf<Pair<String, LocalDate>>()

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
        // Double-tapping the checklist would otherwise launch two coroutines that both read the
        // same pre-write state, so one tap gets swallowed or immediately undone (Issue #111).
        // Same guard as ReminderViewModel.toggleCompletion; keyed on the completion's own
        // (goalCloudId, date) primary key. Only touched from the main thread.
        val key = goal.cloudId to date
        if (!togglesInFlight.add(key)) return
        viewModelScope.launch {
            try {
                val existing = completionDao.getByGoalAndDate(goal.cloudId, date)
                val completion = GoalCompletion(
                    goalCloudId = goal.cloudId,
                    date = date,
                    done = !(existing?.done ?: false),
                    modifiedAt = System.currentTimeMillis()
                )
                completionDao.upsert(completion)
                safeCloudCall(TAG, "pushGoalCompletion") { firebaseManager.pushGoalCompletion(completion) }
            } finally {
                togglesInFlight.remove(key)
            }
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
            val goal = Goal(
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
            goalDao.insertGoal(goal)
            safeCloudCall(TAG, "pushGoal") { firebaseManager.pushGoal(goal) }
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
            val updated = goal.copy(
                name = trimmed,
                type = type,
                metric = metric,
                comparator = comparator,
                threshold = threshold,
                subject = subject?.trim()?.takeIf { it.isNotEmpty() },
                modifiedAt = System.currentTimeMillis()
            )
            goalDao.updateGoal(updated)
            safeCloudCall(TAG, "pushGoal") { firebaseManager.pushGoal(updated) }
        }
    }

    /** Stop a goal counting from today onward, preserving its history (see [activeGoalsOn]). */
    fun archiveGoal(goal: Goal) {
        viewModelScope.launch {
            val updated = goal.copy(archivedDate = LocalDate.now(), modifiedAt = System.currentTimeMillis())
            goalDao.updateGoal(updated)
            safeCloudCall(TAG, "pushGoal") { firebaseManager.pushGoal(updated) }
        }
    }

    fun unarchiveGoal(goal: Goal) {
        viewModelScope.launch {
            val updated = goal.copy(archivedDate = null, modifiedAt = System.currentTimeMillis())
            goalDao.updateGoal(updated)
            safeCloudCall(TAG, "pushGoal") { firebaseManager.pushGoal(updated) }
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            goalDao.deleteGoal(goal)
            // Drop its check-off history too so a re-created cloudId can't inherit stale rows.
            if (goal.cloudId.isNotEmpty()) {
                val completions = completionDao.getByGoal(goal.cloudId)
                completions.forEach { completionDao.delete(it) }
                safeCloudCall(TAG, "deleteGoal") {
                    firebaseManager.deleteGoal(goal.cloudId)
                    completions.forEach { firebaseManager.deleteGoalCompletion(it.goalCloudId, it.date) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DashboardViewModel"
    }
}
