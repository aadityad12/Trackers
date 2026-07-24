package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Issue #42 — study daily-goal streak, weekly totals, and ring fraction. */
class StudyGoalTest {

    private val today = LocalDate.of(2026, 7, 24)
    private fun s(daysAgo: Int, minutes: Int, subject: String = "") =
        StudySession(date = today.minusDays(daysAgo.toLong()), subject = subject, durationSeconds = minutes * 60L)
    private val goal = 60 * 60L // 60 min

    @Test
    fun `streak counts consecutive met days ending today`() {
        val sessions = listOf(s(0, 70), s(1, 60), s(2, 90))
        assertEquals(3, computeStudyStreak(sessions, goal, today))
    }

    @Test
    fun `today not yet met does not break a streak through yesterday`() {
        val sessions = listOf(s(0, 10), s(1, 60), s(2, 80)) // today short, yesterday+ met
        assertEquals(2, computeStudyStreak(sessions, goal, today))
    }

    @Test
    fun `a gap breaks the streak`() {
        val sessions = listOf(s(0, 70), s(1, 5), s(2, 90))
        assertEquals(1, computeStudyStreak(sessions, goal, today))
    }

    @Test
    fun `daily total sums across subjects to meet the goal`() {
        // 35 + 30 = 65 min today across two subjects clears the 60-min goal.
        val sessions = listOf(s(0, 35, "Maths"), s(0, 30, "History"))
        assertEquals(1, computeStudyStreak(sessions, goal, today))
    }

    @Test
    fun `goal off means no streak`() {
        assertEquals(0, computeStudyStreak(listOf(s(0, 200)), 0, today))
    }

    @Test
    fun `weekly minutes are 7 oldest-first with today last`() {
        val week = weeklyStudyMinutes(listOf(s(0, 45), s(3, 30)), 7, today)
        assertEquals(7, week.size)
        assertEquals(today, week.last().first)
        assertEquals(45, week.last().second)
        assertEquals(30, week[3].second)  // 3 days ago
        assertEquals(0, week.first().second) // 6 days ago, empty
    }

    @Test
    fun `goal fraction clamps to one and is zero when off`() {
        assertEquals(0.5f, goalFraction(30 * 60L, goal), 0.001f)
        assertEquals(1f, goalFraction(200 * 60L, goal), 0.001f)
        assertEquals(0f, goalFraction(30 * 60L, 0), 0.001f)
    }
}
