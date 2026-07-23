package com.example.apextracker

import java.time.LocalDate

/**
 * Pure aggregation helpers for the per-subject study model (Issue #78). Kept out of the ViewModel
 * and the composables so the grouping/normalisation rules can be unit-tested without Room or the
 * Android framework (see StudySubjectStatsTest).
 */

/** One subject's total for a single day. */
data class SubjectTotal(val subject: String, val seconds: Long)

/** A day's study rows collapsed into a total plus the per-subject breakdown that produced it. */
data class DayStudy(
    val date: LocalDate,
    val totalSeconds: Long,
    val subjects: List<SubjectTotal>
)

/**
 * Canonical form of a user-entered subject: trimmed, with runs of internal whitespace collapsed to
 * a single space. A blank entry normalises to "" — the "No subject" bucket — so blank and
 * whitespace-only input can never create a distinct row from the uncategorised one.
 */
fun normalizeSubject(input: String): String = input.trim().replace(Regex("\\s+"), " ")

/**
 * Groups raw per-(date, subject) rows into one [DayStudy] per date, most-recent day first. Within a
 * day the subjects are ordered by descending time (ties broken alphabetically, case-insensitively),
 * so the breakdown reads biggest-first. Rows with zero duration are dropped from the breakdown but
 * a day whose every row is zero still yields an entry with an empty subject list and a 0 total.
 */
fun groupSessionsByDate(sessions: List<StudySession>): List<DayStudy> =
    sessions.groupBy { it.date }
        .map { (date, rows) ->
            val subjects = rows
                .groupBy { it.subject }
                .map { (subject, subjectRows) -> SubjectTotal(subject, subjectRows.sumOf { it.durationSeconds }) }
                .filter { it.seconds > 0 }
                .sortedWith(compareByDescending<SubjectTotal> { it.seconds }.thenBy { it.subject.lowercase() })
            DayStudy(date = date, totalSeconds = subjects.sumOf { it.seconds }, subjects = subjects)
        }
        .sortedByDescending { it.date }

/**
 * The distinct named subjects a user has studied before, for offering as quick picks in the
 * subject selector. The empty ("No subject") bucket is excluded — it's always available separately.
 * Sorted case-insensitively; the first-seen casing of each subject wins.
 */
fun knownSubjects(sessions: List<StudySession>): List<String> =
    sessions.asSequence()
        .map { it.subject }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
        .toList()

/**
 * Parses the hours/minutes fields of the manual-entry dialog (Issue #122) into a duration in
 * seconds. Blank means zero, so "2h" and "90m" are both valid shorthand and minutes above 59 just
 * roll into hours. Returns null when either field isn't a non-negative whole number (which the
 * dialog treats as "can't save yet"); zero is a legitimate result — it clears that day's entry.
 */
fun parseManualDurationSeconds(hoursText: String, minutesText: String): Long? {
    val hours = parseNonNegativeInt(hoursText) ?: return null
    val minutes = parseNonNegativeInt(minutesText) ?: return null
    return hours * 3600L + minutes * 60L
}

private fun parseNonNegativeInt(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0L
    val value = trimmed.toLongOrNull() ?: return null
    return if (value < 0) null else value
}
