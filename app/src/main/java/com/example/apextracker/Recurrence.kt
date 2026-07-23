package com.example.apextracker

import androidx.annotation.StringRes
import java.time.DayOfWeek
import java.time.LocalDate

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM
}

enum class RecurrenceEndType {
    NEVER,
    UNTIL_DATE,
    AFTER_OCCURRENCES
}

data class Recurrence(
    val frequency: RecurrenceFrequency,
    val customDays: Set<DayOfWeek>? = null, // for CUSTOM frequency
    val endDate: LocalDate? = null,
    val endOccurrences: Int? = null,
    val endType: RecurrenceEndType,
    // Original day-of-month for MONTHLY/YEARLY chains. plusMonths/plusYears clamp short months
    // (Jan 31 → Feb 28) and re-clamping the clamped date drifts permanently (→ Mar 28 forever);
    // the anchor lets later advances recover the intended day. Null on pre-existing persisted
    // data (Gson) — filled in lazily on the next advancement.
    val anchorDay: Int? = null
)

/** Returns this recurrence with [anchorDay] filled from [date] if it applies and isn't set yet. */
fun Recurrence.withAnchorFrom(date: LocalDate): Recurrence =
    if (anchorDay != null || (frequency != RecurrenceFrequency.MONTHLY && frequency != RecurrenceFrequency.YEARLY)) this
    else copy(anchorDay = date.dayOfMonth)

/**
 * First date on the recurrence grid strictly after both [currentDate] and [today], or null if
 * the recurrence can't advance. When a recurring reminder sat overdue, this skips the missed
 * periods instead of generating a pile-up of past occurrences — while staying on the chain's
 * grid (a weekly-Monday reminder completed on a Thursday still advances to a Monday).
 * For an on-time completion ([currentDate] >= [today]) this is one plain advance.
 */
fun calculateNextOccurrenceAfter(currentDate: LocalDate, recurrence: Recurrence, today: LocalDate): LocalDate? {
    var next = calculateNextDate(currentDate, recurrence) ?: return null
    var guard = 0 // defensive bound; a daily reminder missed for ~27 years is the worst case
    while (!next.isAfter(today) && guard++ < 10_000) {
        next = calculateNextDate(next, recurrence) ?: return null
    }
    return if (next.isAfter(today)) next else null
}

/**
 * Advances [currentDate] by one recurrence period, or null if the recurrence can't advance
 * (CUSTOM with no days). Monthly/yearly advancement targets [Recurrence.anchorDay] (falling back
 * to the current day-of-month), clamped to the target month's length.
 */
fun calculateNextDate(currentDate: LocalDate, recurrence: Recurrence): LocalDate? {
    return when (recurrence.frequency) {
        RecurrenceFrequency.DAILY -> currentDate.plusDays(1)
        RecurrenceFrequency.WEEKLY -> currentDate.plusWeeks(1)
        RecurrenceFrequency.MONTHLY -> {
            val anchor = recurrence.anchorDay ?: currentDate.dayOfMonth
            val next = currentDate.plusMonths(1)
            next.withDayOfMonth(minOf(anchor, next.lengthOfMonth()))
        }
        RecurrenceFrequency.YEARLY -> {
            val anchor = recurrence.anchorDay ?: currentDate.dayOfMonth
            val next = currentDate.plusYears(1)
            next.withDayOfMonth(minOf(anchor, next.lengthOfMonth()))
        }
        RecurrenceFrequency.CUSTOM -> {
            val days = recurrence.customDays ?: return null
            if (days.isEmpty()) return null

            var next = currentDate.plusDays(1)
            // Search for the next day of week in the set
            repeat(7) {
                if (days.contains(next.dayOfWeek)) return next
                next = next.plusDays(1)
            }
            null
        }
    }
}

/**
 * Display names for the recurrence enums (Issue #112) — the pickers and the reminder summary used
 * to render raw constant names ("AFTER_OCCURRENCES"), which never localize. Same pattern as
 * GoalsView's goal-enum labels.
 */
@StringRes
fun frequencyLabelRes(frequency: RecurrenceFrequency): Int = when (frequency) {
    RecurrenceFrequency.DAILY -> R.string.recurrence_freq_daily
    RecurrenceFrequency.WEEKLY -> R.string.recurrence_freq_weekly
    RecurrenceFrequency.MONTHLY -> R.string.recurrence_freq_monthly
    RecurrenceFrequency.YEARLY -> R.string.recurrence_freq_yearly
    RecurrenceFrequency.CUSTOM -> R.string.recurrence_freq_custom
}

@StringRes
fun endTypeLabelRes(endType: RecurrenceEndType): Int = when (endType) {
    RecurrenceEndType.NEVER -> R.string.recurrence_end_never
    RecurrenceEndType.UNTIL_DATE -> R.string.recurrence_end_until_date
    RecurrenceEndType.AFTER_OCCURRENCES -> R.string.recurrence_end_after_occurrences
}
