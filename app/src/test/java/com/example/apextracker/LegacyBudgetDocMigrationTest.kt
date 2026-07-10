package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LegacyBudgetDocMigrationTest {

    private val fallback = LocalDate.of(2026, 7, 9)

    private fun localItem(title: String, amount: Double, date: LocalDate) =
        BudgetItem(id = 1, title = title, amount = amount, date = date, cloudId = "some-uuid", modifiedAt = 1L)

    @Test
    fun `doc with a proper cloudId is not legacy`() {
        val action = classifyLegacyBudgetDoc(
            mapOf("cloudId" to "uuid-1", "title" to "Coffee", "amount" to 3.5),
            emptyList(), fallback
        )
        assertNull(action)
    }

    @Test
    fun `blank and missing cloudId are both legacy`() {
        val blank = classifyLegacyBudgetDoc(
            mapOf("cloudId" to "", "title" to "Coffee", "amount" to 3.5, "date" to "2026-07-01"),
            emptyList(), fallback
        )
        val missing = classifyLegacyBudgetDoc(
            mapOf("title" to "Coffee", "amount" to 3.5, "date" to "2026-07-01"),
            emptyList(), fallback
        )
        assertTrue(blank is LegacyBudgetDocAction.Migrate)
        assertTrue(missing is LegacyBudgetDocAction.Migrate)
    }

    @Test
    fun `duplicate of a local item is delete-only`() {
        val action = classifyLegacyBudgetDoc(
            mapOf("cloudId" to "", "title" to "Coffee", "amount" to 3.5, "date" to "2026-07-01"),
            listOf(localItem("Coffee", 3.5, LocalDate.of(2026, 7, 1))),
            fallback
        )
        assertEquals(LegacyBudgetDocAction.DeleteOnly, action)
    }

    @Test
    fun `same title and amount with unreadable date still counts as duplicate`() {
        // When the legacy date can't be parsed, title+amount alone match the local row
        val action = classifyLegacyBudgetDoc(
            mapOf("cloudId" to "", "title" to "Coffee", "amount" to 3.5, "date" to 12345L),
            listOf(localItem("Coffee", 3.5, LocalDate.of(2026, 6, 15))),
            fallback
        )
        assertEquals(LegacyBudgetDocAction.DeleteOnly, action)
    }

    @Test
    fun `same title and amount but different readable date is not a duplicate`() {
        val action = classifyLegacyBudgetDoc(
            mapOf("cloudId" to "", "title" to "Coffee", "amount" to 3.5, "date" to "2026-07-02"),
            listOf(localItem("Coffee", 3.5, LocalDate.of(2026, 7, 1))),
            fallback
        )
        assertTrue(action is LegacyBudgetDocAction.Migrate)
    }

    @Test
    fun `missing title or amount is unsalvageable`() {
        val noTitle = classifyLegacyBudgetDoc(
            mapOf("cloudId" to "", "amount" to 3.5), emptyList(), fallback
        )
        val noAmount = classifyLegacyBudgetDoc(
            mapOf("cloudId" to "", "title" to "Coffee"), emptyList(), fallback
        )
        assertEquals(LegacyBudgetDocAction.DeleteOnly, noTitle)
        assertEquals(LegacyBudgetDocAction.DeleteOnly, noAmount)
    }

    @Test
    fun `migrates with ISO string date`() {
        val action = classifyLegacyBudgetDoc(
            mapOf("cloudId" to "", "title" to "Rent", "amount" to 1200L, "date" to "2026-06-01", "description" to "june"),
            emptyList(), fallback
        ) as LegacyBudgetDocAction.Migrate
        assertEquals("Rent", action.item.title)
        assertEquals(1200.0, action.item.amount, 0.0) // Long amount coerced
        assertEquals(LocalDate.of(2026, 6, 1), action.item.date)
        assertEquals("june", action.item.description)
        assertNull(action.item.categoryId) // legacy local category id is dropped
    }

    @Test
    fun `migrates with nested-map date from raw POJO serialization`() {
        val action = classifyLegacyBudgetDoc(
            mapOf(
                "cloudId" to "", "title" to "Rent", "amount" to 1200.0,
                "date" to mapOf("year" to 2026L, "monthValue" to 6L, "dayOfMonth" to 1L, "dayOfWeek" to "MONDAY")
            ),
            emptyList(), fallback
        ) as LegacyBudgetDocAction.Migrate
        assertEquals(LocalDate.of(2026, 6, 1), action.item.date)
    }

    @Test
    fun `unreadable date falls back to the provided date`() {
        val action = classifyLegacyBudgetDoc(
            mapOf("cloudId" to "", "title" to "Rent", "amount" to 1200.0, "date" to "garbage"),
            emptyList(), fallback
        ) as LegacyBudgetDocAction.Migrate
        assertEquals(fallback, action.item.date)
    }
}
