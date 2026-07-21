package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Goal.type values. Stored as plain strings (no TypeConverter) — matches the primitive convention. */
object GoalType {
    const val MANUAL = "MANUAL"
    const val AUTO = "AUTO"
}

/** Goal.metric values (AUTO goals only). Each maps to an existing per-day tracker total. */
object GoalMetric {
    const val SCREEN_TIME = "SCREEN_TIME" // threshold in hours, evaluated against ScreenTimeSession.durationMillis
    const val STUDY = "STUDY"             // threshold in hours, evaluated against summed StudySession.durationSeconds
    const val SPEND = "SPEND"             // threshold in currency units, evaluated against summed BudgetItem.amount
}

/** Goal.comparator values (AUTO goals only). */
object GoalComparator {
    const val UNDER = "UNDER" // satisfied when the day's value is at or below the threshold
    const val OVER = "OVER"   // satisfied when the day's value is at or above the threshold
}

/**
 * A daily habit-style goal tracked on the Dashboard heatmap. Distinct from [Reminder]s (one-off /
 * recurring to-dos) — goals feed the contribution graph, reminders never do.
 *
 * MANUAL goals are ticked by hand and their per-day state lives in [GoalCompletion]. AUTO goals
 * carry a [metric] + [comparator] + [threshold] and are evaluated on read from existing tracker
 * tables — nothing is stored per day for them (see DashboardScoring).
 *
 * [startDate] is when the goal began counting; [archivedDate] (null = active) is the first day it
 * stops counting. A given day only counts goals active on that day, so editing goals never
 * retroactively rewrites past days.
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,                 // GoalType.*
    val metric: String? = null,       // GoalMetric.* (AUTO only)
    val comparator: String? = null,   // GoalComparator.* (AUTO only)
    val threshold: Double? = null,    // hours (SCREEN_TIME/STUDY) or currency (SPEND)
    val subject: String? = null,      // STUDY scope: null = all subjects, non-null = that subject only
    val startDate: LocalDate = LocalDate.now(),
    val archivedDate: LocalDate? = null,
    val sortOrder: Int = 0,
    val cloudId: String = "",
    val modifiedAt: Long = 0L
)
