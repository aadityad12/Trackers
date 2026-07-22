package com.example.apextracker

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class FirebaseDocParsingTest {

    private val gson = Gson()

    // ── Category ──────────────────────────────────────────────────────────────

    @Test
    fun `category doc round-trips`() {
        val parsed = parseCategoryDoc(
            mapOf("cloudId" to "cat-1", "name" to "Groceries", "colorHex" to "#FF0000", "modifiedAt" to 42L)
        )
        assertEquals("cat-1", parsed.cloudId)
        assertEquals("Groceries", parsed.name)
        assertEquals("#FF0000", parsed.colorHex)
        assertEquals(42L, parsed.modifiedAt)
    }

    @Test
    fun `category doc round-trips a monthly limit`() {
        val parsed = parseCategoryDoc(
            mapOf("cloudId" to "cat-1", "name" to "Groceries", "colorHex" to "#FF0000", "monthlyLimit" to 400.0)
        )
        assertEquals(400.0, parsed.monthlyLimit!!, 0.0001)
    }

    @Test
    fun `category doc without a monthly limit parses as uncapped`() {
        // Docs written before per-category limits existed have no such field.
        val parsed = parseCategoryDoc(
            mapOf("cloudId" to "cat-1", "name" to "Groceries", "colorHex" to "#FF0000")
        )
        assertNull(parsed.monthlyLimit)
    }

    @Test
    fun `category doc with an explicitly null monthly limit parses as uncapped`() {
        // Clearing a cap pushes null rather than omitting the field.
        val parsed = parseCategoryDoc(
            mapOf("cloudId" to "cat-1", "name" to "Groceries", "colorHex" to "#FF0000", "monthlyLimit" to null)
        )
        assertNull(parsed.monthlyLimit)
    }

    @Test
    fun `category monthly limit accepts a Long from Firestore`() {
        // Firestore hands back whole numbers as Long, not Double.
        val parsed = parseCategoryDoc(
            mapOf("cloudId" to "cat-1", "name" to "Groceries", "colorHex" to "#FF0000", "monthlyLimit" to 400L)
        )
        assertEquals(400.0, parsed.monthlyLimit!!, 0.0001)
    }

    @Test
    fun `category doc with missing name throws`() {
        assertThrows(IllegalStateException::class.java) {
            parseCategoryDoc(mapOf("cloudId" to "cat-1", "colorHex" to "#FF0000"))
        }
    }

    @Test
    fun `blank cloudId throws — legacy docs must not be re-imported`() {
        // Legacy docs written by the old BudgetViewModel path serialized cloudId = "".
        // Re-importing them created duplicate items with fresh UUIDs on every sign-in.
        assertThrows(IllegalStateException::class.java) {
            parseBudgetItemDoc(
                mapOf("cloudId" to "", "title" to "Coffee", "amount" to 3.5, "date" to "2026-07-09")
            )
        }
        assertThrows(IllegalStateException::class.java) {
            parseCategoryDoc(mapOf("cloudId" to "", "name" to "X", "colorHex" to "#000000"))
        }
    }

    @Test
    fun `missing cloudId throws`() {
        assertThrows(IllegalStateException::class.java) {
            parseBudgetItemDoc(mapOf("title" to "Coffee", "amount" to 3.5, "date" to "2026-07-09"))
        }
    }

    // ── BudgetItem ────────────────────────────────────────────────────────────

    @Test
    fun `budget item doc round-trips with Double amount`() {
        val (item, categoryCloudId) = parseBudgetItemDoc(
            mapOf(
                "cloudId" to "b-1", "title" to "Coffee", "amount" to 3.5,
                "description" to "latte", "date" to "2026-07-09",
                "categoryCloudId" to "cat-1", "modifiedAt" to 100L
            )
        )
        assertEquals("b-1", item.cloudId)
        assertEquals("Coffee", item.title)
        assertEquals(3.5, item.amount, 0.0)
        assertEquals("latte", item.description)
        assertEquals(LocalDate.of(2026, 7, 9), item.date)
        assertNull(item.categoryId) // FK resolved by the caller, not the parser
        assertEquals("cat-1", categoryCloudId)
        assertEquals(100L, item.modifiedAt)
    }

    @Test
    fun `budget item amount stored as Long is coerced to Double`() {
        // Firestore returns whole numbers as Long
        val (item, _) = parseBudgetItemDoc(
            mapOf("cloudId" to "b-1", "title" to "Rent", "amount" to 1200L, "date" to "2026-07-01")
        )
        assertEquals(1200.0, item.amount, 0.0)
    }

    @Test
    fun `budget item optional fields default`() {
        val (item, categoryCloudId) = parseBudgetItemDoc(
            mapOf("cloudId" to "b-1", "title" to "Rent", "amount" to 1200.0, "date" to "2026-07-01")
        )
        assertNull(item.description)
        assertNull(categoryCloudId)
        assertEquals(0L, item.modifiedAt)
    }

    @Test
    fun `budget item with unparseable date throws`() {
        assertThrows(Exception::class.java) {
            parseBudgetItemDoc(
                mapOf("cloudId" to "b-1", "title" to "Rent", "amount" to 1200.0, "date" to "not-a-date")
            )
        }
    }

    @Test
    fun `budget item with missing title throws`() {
        assertThrows(IllegalStateException::class.java) {
            parseBudgetItemDoc(mapOf("cloudId" to "b-1", "amount" to 1200.0, "date" to "2026-07-01"))
        }
    }

    // ── Subscription ──────────────────────────────────────────────────────────

    @Test
    fun `subscription doc round-trips`() {
        val parsed = parseSubscriptionDoc(
            mapOf(
                "cloudId" to "s-1", "name" to "Netflix", "amount" to 15L,
                "renewalDate" to "2026-08-01", "notes" to "family plan",
                "lastAddedDate" to "2026-07-01", "modifiedAt" to 7L
            )
        )
        assertEquals("s-1", parsed.cloudId)
        assertEquals("Netflix", parsed.name)
        assertEquals(15.0, parsed.amount, 0.0)
        assertEquals(LocalDate.of(2026, 8, 1), parsed.renewalDate)
        assertEquals("family plan", parsed.notes)
        assertEquals(LocalDate.of(2026, 7, 1), parsed.lastAddedDate)
        assertEquals(7L, parsed.modifiedAt)
    }

    @Test
    fun `subscription without lastAddedDate parses`() {
        val parsed = parseSubscriptionDoc(
            mapOf("cloudId" to "s-1", "name" to "Netflix", "amount" to 15.0, "renewalDate" to "2026-08-01")
        )
        assertNull(parsed.lastAddedDate)
        assertNull(parsed.notes)
    }

    // ── Note ──────────────────────────────────────────────────────────────────

    @Test
    fun `note doc round-trips including soft-delete state and pin`() {
        val parsed = parseNoteDoc(
            mapOf(
                "cloudId" to "n-1", "title" to "Shopping", "content" to "- milk",
                "createdAt" to "2026-07-01T10:00:00", "modifiedAt" to "2026-07-09T12:30:00",
                "isDeleted" to true, "deletedAt" to "2026-07-09T12:30:00", "isPinned" to true
            )
        )
        assertEquals("n-1", parsed.cloudId)
        assertEquals("Shopping", parsed.title)
        assertEquals("- milk", parsed.content)
        assertEquals(LocalDateTime.of(2026, 7, 1, 10, 0), parsed.createdAt)
        assertEquals(LocalDateTime.of(2026, 7, 9, 12, 30), parsed.modifiedAt)
        assertEquals(true, parsed.isDeleted)
        assertEquals(LocalDateTime.of(2026, 7, 9, 12, 30), parsed.deletedAt)
        assertEquals(true, parsed.isPinned)
    }

    @Test
    fun `note doc without soft-delete or pin fields defaults to not deleted and not pinned`() {
        val parsed = parseNoteDoc(
            mapOf(
                "cloudId" to "n-1", "title" to "", "content" to "x",
                "createdAt" to "2026-07-01T10:00:00", "modifiedAt" to "2026-07-09T12:30:00"
            )
        )
        assertFalse(parsed.isDeleted)
        assertNull(parsed.deletedAt)
        assertEquals("", parsed.title) // empty (but present) title is legitimate
        assertFalse(parsed.isPinned) // old cloud docs written before pinning existed still parse fine
    }

    @Test
    fun `note doc with bad modifiedAt throws`() {
        assertThrows(Exception::class.java) {
            parseNoteDoc(
                mapOf(
                    "cloudId" to "n-1", "title" to "t", "content" to "c",
                    "createdAt" to "2026-07-01T10:00:00", "modifiedAt" to "yesterday"
                )
            )
        }
    }

    // ── Reminder ──────────────────────────────────────────────────────────────

    @Test
    fun `reminder doc round-trips including recurrence via Gson`() {
        val recurrence = Recurrence(
            frequency = RecurrenceFrequency.CUSTOM,
            customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            endDate = LocalDate.of(2026, 12, 31),
            endOccurrences = null,
            endType = RecurrenceEndType.UNTIL_DATE
        )
        val parsed = parseReminderDoc(
            mapOf(
                "cloudId" to "r-1", "name" to "Gym", "date" to "2026-07-10",
                "time" to "07:30", "description" to "leg day", "isCompleted" to false,
                "recurrence" to gson.toJson(recurrence), "parentCloudId" to "r-0",
                "occurrencesCompleted" to 3L, "modifiedAt" to 9L
            ),
            gson
        )
        assertEquals("r-1", parsed.cloudId)
        assertEquals("Gym", parsed.name)
        assertEquals(LocalDate.of(2026, 7, 10), parsed.date)
        assertEquals(LocalTime.of(7, 30), parsed.time)
        assertEquals("leg day", parsed.description)
        assertFalse(parsed.isCompleted)
        assertEquals(recurrence, parsed.recurrence)
        assertEquals("r-0", parsed.parentCloudId)
        assertEquals(3, parsed.occurrencesCompleted)
        assertEquals(9L, parsed.modifiedAt)
    }

    @Test
    fun `all-day reminder without time parses`() {
        val parsed = parseReminderDoc(
            mapOf("cloudId" to "r-1", "name" to "Pay rent", "date" to "2026-08-01"),
            gson
        )
        assertNull(parsed.time)
        assertNull(parsed.recurrence)
        assertNull(parsed.parentCloudId)
        assertEquals(0, parsed.occurrencesCompleted)
    }

    @Test
    fun `reminder with bad date throws`() {
        assertThrows(Exception::class.java) {
            parseReminderDoc(mapOf("cloudId" to "r-1", "name" to "x", "date" to ""), gson)
        }
    }

    // ── StudySession ──────────────────────────────────────────────────────────

    @Test
    fun `study session doc round-trips`() {
        val parsed = parseStudySessionDoc(mapOf("date" to "2026-07-09", "subject" to "Math", "durationSeconds" to 3600L))
        assertEquals(LocalDate.of(2026, 7, 9), parsed.date)
        assertEquals("Math", parsed.subject)
        assertEquals(3600L, parsed.durationSeconds)
    }

    @Test
    fun `legacy study session doc without subject parses as no-subject bucket`() {
        val parsed = parseStudySessionDoc(mapOf("date" to "2026-07-09", "durationSeconds" to 3600L))
        assertEquals("", parsed.subject)
        assertEquals(3600L, parsed.durationSeconds)
    }

    @Test
    fun `study session without duration throws`() {
        assertThrows(IllegalStateException::class.java) {
            parseStudySessionDoc(mapOf("date" to "2026-07-09"))
        }
    }

    @Test
    fun `study session doc id keeps bare date for no-subject bucket`() {
        assertEquals("2026-07-09", studySessionDocId(LocalDate.of(2026, 7, 9), ""))
    }

    @Test
    fun `study session doc id appends subject and sanitizes slashes`() {
        assertEquals("2026-07-09|Math", studySessionDocId(LocalDate.of(2026, 7, 9), "Math"))
        assertEquals("2026-07-09|CS_Algorithms", studySessionDocId(LocalDate.of(2026, 7, 9), "CS/Algorithms"))
    }

    // ── Goals ────────────────────────────────────────────────────────────────

    @Test
    fun `manual goal doc parses with auto fields absent`() {
        val parsed = parseGoalDoc(
            mapOf(
                "cloudId" to "g1", "name" to "Workout", "type" to "MANUAL",
                "startDate" to "2026-07-01", "sortOrder" to 2L, "modifiedAt" to 555L
            )
        )
        assertEquals("g1", parsed.cloudId)
        assertEquals("MANUAL", parsed.type)
        assertNull(parsed.metric)
        assertNull(parsed.threshold)
        assertNull(parsed.archivedDate)
        assertEquals(2, parsed.sortOrder)
        assertEquals(LocalDate.of(2026, 7, 1), parsed.startDate)
    }

    @Test
    fun `auto goal doc parses metric direction threshold and archived date`() {
        val parsed = parseGoalDoc(
            mapOf(
                "cloudId" to "g2", "name" to "Screen", "type" to "AUTO",
                "metric" to "SCREEN_TIME", "comparator" to "UNDER", "threshold" to 6.0,
                "subject" to "Work", "startDate" to "2026-07-01", "archivedDate" to "2026-07-10",
                "modifiedAt" to 999L
            )
        )
        assertEquals("SCREEN_TIME", parsed.metric)
        assertEquals("UNDER", parsed.comparator)
        assertEquals(6.0, parsed.threshold!!, 0.0001)
        assertEquals("Work", parsed.subject)
        assertEquals(LocalDate.of(2026, 7, 10), parsed.archivedDate)
    }

    @Test
    fun `goal doc without name throws`() {
        assertThrows(IllegalStateException::class.java) {
            parseGoalDoc(mapOf("cloudId" to "g1", "type" to "MANUAL", "startDate" to "2026-07-01"))
        }
    }

    @Test
    fun `goal doc with blank cloudId throws`() {
        assertThrows(IllegalStateException::class.java) {
            parseGoalDoc(mapOf("cloudId" to "", "name" to "X", "type" to "MANUAL", "startDate" to "2026-07-01"))
        }
    }

    @Test
    fun `goal completion doc parses`() {
        val parsed = parseGoalCompletionDoc(
            mapOf("goalCloudId" to "g1", "date" to "2026-07-21", "done" to true, "modifiedAt" to 123L)
        )
        assertEquals("g1", parsed.goalCloudId)
        assertEquals(LocalDate.of(2026, 7, 21), parsed.date)
        assertEquals(true, parsed.done)
        assertEquals(123L, parsed.modifiedAt)
    }

    @Test
    fun `goal completion doc id is goalCloudId piped with date`() {
        assertEquals("g1|2026-07-21", goalCompletionDocId("g1", LocalDate.of(2026, 7, 21)))
    }
}
