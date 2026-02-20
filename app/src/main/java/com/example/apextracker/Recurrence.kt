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
    val endType: RecurrenceEndType
)
