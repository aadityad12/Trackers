package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StudySubjectStatsTest {
    private fun session(date: String, subject: String, seconds: Long) =
        StudySession(date = LocalDate.parse(date), subject = subject, durationSeconds = seconds)

    // ── normalizeSubject ──────────────────────────────────────────────────────

    @Test
    fun `blank and whitespace-only subjects normalize to the empty bucket`() {
        assertEquals("", normalizeSubject(""))
        assertEquals("", normalizeSubject("   "))
        assertEquals("", normalizeSubject("\t\n"))
    }

    @Test
    fun `normalize trims edges and collapses internal whitespace`() {
        assertEquals("Organic Chemistry", normalizeSubject("  Organic   Chemistry  "))
    }

    // ── groupSessionsByDate ───────────────────────────────────────────────────

    @Test
    fun `groups rows per date and sums the day total`() {
        val days = groupSessionsByDate(
            listOf(
                session("2026-07-09", "Math", 600),
                session("2026-07-09", "History", 300),
                session("2026-07-08", "Math", 1200)
            )
        )
        assertEquals(2, days.size)
        // Most recent day first
        assertEquals(LocalDate.of(2026, 7, 9), days[0].date)
        assertEquals(900, days[0].totalSeconds)
        assertEquals(1200, days[1].totalSeconds)
    }

    @Test
    fun `within a day subjects are ordered by descending time then alphabetically`() {
        val day = groupSessionsByDate(
            listOf(
                session("2026-07-09", "History", 300),
                session("2026-07-09", "Math", 900),
                session("2026-07-09", "Biology", 300)
            )
        ).single()
        assertEquals(listOf("Math", "Biology", "History"), day.subjects.map { it.subject })
    }

    @Test
    fun `zero-duration rows drop from breakdown but the day still appears`() {
        val day = groupSessionsByDate(listOf(session("2026-07-09", "Math", 0))).single()
        assertEquals(0, day.totalSeconds)
        assertEquals(emptyList<SubjectTotal>(), day.subjects)
    }

    @Test
    fun `the empty subject survives as its own bucket`() {
        val day = groupSessionsByDate(
            listOf(
                session("2026-07-09", "", 600),
                session("2026-07-09", "Math", 300)
            )
        ).single()
        assertEquals(900, day.totalSeconds)
        assertEquals(600, day.subjects.first { it.subject == "" }.seconds)
    }

    // ── knownSubjects ─────────────────────────────────────────────────────────

    @Test
    fun `known subjects excludes the blank bucket and dedupes case-insensitively`() {
        val subjects = knownSubjects(
            listOf(
                session("2026-07-09", "Math", 600),
                session("2026-07-08", "math", 300),
                session("2026-07-07", "", 300),
                session("2026-07-06", "Biology", 300)
            )
        )
        assertEquals(listOf("Biology", "Math"), subjects)
    }
}
