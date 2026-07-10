package com.example.apextracker

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
