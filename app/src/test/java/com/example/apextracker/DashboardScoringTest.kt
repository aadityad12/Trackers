package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardScoringTest {

    private val today = LocalDate.of(2026, 7, 21)

    private fun manual(cloudId: String, start: LocalDate = today.minusDays(30), archived: LocalDate? = null) =
        Goal(name = "Workout", type = GoalType.MANUAL, cloudId = cloudId, startDate = start, archivedDate = archived)

    private fun auto(
        metric: String,
        comparator: String,
        threshold: Double,
        subject: String? = null,
        start: LocalDate = today.minusDays(30),
        archived: LocalDate? = null
    ) = Goal(
        name = "Auto",
        type = GoalType.AUTO,
        metric = metric,
        comparator = comparator,
        threshold = threshold,
        subject = subject,
        startDate = start,
        archivedDate = archived
    )

    private fun done(cloudId: String, date: LocalDate = today) =
        GoalCompletion(goalCloudId = cloudId, date = date, done = true)

    // ── activeGoalsOn ────────────────────────────────────────────────────────

    @Test
    fun `goal counts from its start date, not before`() {
        val g = manual("a", start = today)
        assertTrue(activeGoalsOn(today, listOf(g)).isNotEmpty())
        assertTrue(activeGoalsOn(today.minusDays(1), listOf(g)).isEmpty())
    }

    @Test
    fun `archived goal stops counting on and after its archive date`() {
        val g = manual("a", start = today.minusDays(10), archived = today)
        assertTrue(activeGoalsOn(today.minusDays(1), listOf(g)).isNotEmpty())
        assertTrue(activeGoalsOn(today, listOf(g)).isEmpty())
    }

    // ── evaluateAutoGoal ─────────────────────────────────────────────────────

    @Test
    fun `screen time under threshold is met at or below`() {
        val g = auto(GoalMetric.SCREEN_TIME, GoalComparator.UNDER, 6.0)
        assertTrue(evaluateAutoGoal(g, DayMetrics(screenMillis = 5 * 3_600_000L)))
        assertTrue(evaluateAutoGoal(g, DayMetrics(screenMillis = 6 * 3_600_000L))) // exactly at target
        assertFalse(evaluateAutoGoal(g, DayMetrics(screenMillis = 7 * 3_600_000L)))
    }

    @Test
    fun `study over threshold is met at or above`() {
        val g = auto(GoalMetric.STUDY, GoalComparator.OVER, 3.0)
        assertFalse(evaluateAutoGoal(g, DayMetrics(studyBySubject = mapOf("" to 2 * 3_600L))))
        assertTrue(evaluateAutoGoal(g, DayMetrics(studyBySubject = mapOf("" to 3 * 3_600L))))
        assertTrue(evaluateAutoGoal(g, DayMetrics(studyBySubject = mapOf("Work" to 4 * 3_600L))))
    }

    @Test
    fun `study goal with null subject sums all subjects`() {
        val g = auto(GoalMetric.STUDY, GoalComparator.OVER, 3.0, subject = null)
        val metrics = DayMetrics(studyBySubject = mapOf("Work" to 2 * 3_600L, "Math" to 2 * 3_600L))
        assertTrue(evaluateAutoGoal(g, metrics)) // 4h total
    }

    @Test
    fun `study goal scoped to a subject only counts that subject`() {
        val g = auto(GoalMetric.STUDY, GoalComparator.OVER, 3.0, subject = "Work")
        val metrics = DayMetrics(studyBySubject = mapOf("Work" to 2 * 3_600L, "Math" to 5 * 3_600L))
        assertFalse(evaluateAutoGoal(g, metrics)) // only 2h of Work despite 7h total
    }

    @Test
    fun `spend under threshold uses raw currency value`() {
        val g = auto(GoalMetric.SPEND, GoalComparator.UNDER, 50.0)
        assertTrue(evaluateAutoGoal(g, DayMetrics(spend = 40.0)))
        assertFalse(evaluateAutoGoal(g, DayMetrics(spend = 60.0)))
    }

    @Test
    fun `malformed auto goal is never satisfied`() {
        val noThreshold = auto(GoalMetric.SPEND, GoalComparator.UNDER, 0.0).copy(threshold = null)
        assertFalse(evaluateAutoGoal(noThreshold, DayMetrics(spend = 0.0)))
        val badMetric = auto("BOGUS", GoalComparator.OVER, 1.0)
        assertFalse(evaluateAutoGoal(badMetric, DayMetrics()))
    }

    // ── isGoalSatisfied ──────────────────────────────────────────────────────

    @Test
    fun `manual goal is satisfied only with a done completion for that day`() {
        val g = manual("a")
        assertTrue(isGoalSatisfied(g, today, listOf(done("a")), DayMetrics()))
        assertFalse(isGoalSatisfied(g, today, emptyList(), DayMetrics()))
        assertFalse(isGoalSatisfied(g, today, listOf(done("a", today.minusDays(1))), DayMetrics()))
    }

    @Test
    fun `unchecked completion row does not satisfy`() {
        val g = manual("a")
        val undone = GoalCompletion(goalCloudId = "a", date = today, done = false)
        assertFalse(isGoalSatisfied(g, today, listOf(undone), DayMetrics()))
    }

    // ── dayFraction ──────────────────────────────────────────────────────────

    @Test
    fun `no active goals yields null fraction`() {
        val g = manual("a", start = today.plusDays(1))
        assertNull(dayFraction(today, listOf(g), emptyList(), DayMetrics()))
    }

    @Test
    fun `fraction is satisfied over active count`() {
        val goals = listOf(
            manual("a"),
            manual("b"),
            auto(GoalMetric.SCREEN_TIME, GoalComparator.UNDER, 6.0),
            auto(GoalMetric.STUDY, GoalComparator.OVER, 3.0)
        )
        // a done, b not; screen ok (5h), study not (2h) -> 2 of 4
        val metrics = DayMetrics(screenMillis = 5 * 3_600_000L, studyBySubject = mapOf("" to 2 * 3_600L))
        assertEquals(0.5, dayFraction(today, goals, listOf(done("a")), metrics)!!, 0.0001)
    }

    @Test
    fun `denominator excludes goals not yet started`() {
        val goals = listOf(manual("a"), manual("b", start = today.plusDays(5)))
        // only "a" is active today -> 1 of 1
        assertEquals(1.0, dayFraction(today, goals, listOf(done("a")), DayMetrics())!!, 0.0001)
    }

    // ── intensityBucket ──────────────────────────────────────────────────────

    @Test
    fun `intensity buckets map fractions to 0 through 4`() {
        assertEquals(0, intensityBucket(0.0))
        assertEquals(1, intensityBucket(0.25))
        assertEquals(2, intensityBucket(0.5))
        assertEquals(3, intensityBucket(0.75))
        assertEquals(3, intensityBucket(0.99))
        assertEquals(4, intensityBucket(1.0))
    }

    // ── streaks ──────────────────────────────────────────────────────────────

    @Test
    fun `perfect day streak counts back until a non-perfect day`() {
        val g = manual("a")
        val completions = listOf(done("a", today), done("a", today.minusDays(1)))
        // today and yesterday perfect, two days ago not
        assertEquals(2, perfectDayStreak(today, listOf(g), completions) { DayMetrics() })
    }

    @Test
    fun `perfect day streak is zero when today is not perfect`() {
        val g = manual("a")
        assertEquals(0, perfectDayStreak(today, listOf(g), emptyList()) { DayMetrics() })
    }

    @Test
    fun `perfect day streak breaks on a day with no active goals`() {
        // goal started today only; yesterday had no active goals -> streak stops at 1
        val g = manual("a", start = today)
        val completions = listOf(done("a", today))
        assertEquals(1, perfectDayStreak(today, listOf(g), completions) { DayMetrics() })
    }

    @Test
    fun `goal streak counts consecutive satisfied days and stops at a gap`() {
        val g = manual("a")
        val completions = listOf(done("a", today), done("a", today.minusDays(1)), done("a", today.minusDays(3)))
        assertEquals(2, goalStreak(g, today, completions) { DayMetrics() })
    }

    @Test
    fun `goal streak does not extend before the goal started`() {
        val g = manual("a", start = today.minusDays(1))
        val completions = listOf(done("a", today), done("a", today.minusDays(1)), done("a", today.minusDays(2)))
        assertEquals(2, goalStreak(g, today, completions) { DayMetrics() }) // day -2 predates start
    }
}
