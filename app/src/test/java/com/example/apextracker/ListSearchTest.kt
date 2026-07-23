package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Issue #123 — search/filter for the Reminders and Budget lists. */
class ListSearchTest {

    private fun reminder(name: String, description: String? = null) =
        Reminder(name = name, date = LocalDate.of(2026, 7, 23), description = description)

    private fun item(title: String, description: String? = null, categoryId: Long? = null) =
        BudgetItem(title = title, amount = 1.0, description = description, categoryId = categoryId)

    @Test
    fun `blank query matches everything`() {
        val list = listOf(reminder("Dentist"), reminder("Taxes"))
        assertEquals(list, filterReminders(list, ""))
        assertEquals(list, filterReminders(list, "   "))
        assertTrue(matchesQuery("", null))
    }

    @Test
    fun `reminders match name or description, case-insensitively`() {
        val list = listOf(reminder("Dentist"), reminder("Taxes", "file with HMRC"), reminder("Gym"))
        assertEquals(listOf("Dentist"), filterReminders(list, "dent").map { it.name })
        assertEquals(listOf("Taxes"), filterReminders(list, "hmrc").map { it.name })
        assertTrue(filterReminders(list, "nothing here").isEmpty())
    }

    @Test
    fun `budget items match title, description, or category name`() {
        val names = mapOf(3L to "Groceries")
        val list = listOf(
            item("Milk", categoryId = 3L),
            item("Train ticket", description = "to Leeds"),
            item("Cinema")
        )
        assertEquals(listOf("Milk"), filterBudgetItems(list, names, "grocer").map { it.title })
        assertEquals(listOf("Train ticket"), filterBudgetItems(list, names, "leeds").map { it.title })
        assertEquals(listOf("Cinema"), filterBudgetItems(list, names, "cine").map { it.title })
    }

    @Test
    fun `legacy subscription prefix does not affect matching`() {
        val list = listOf(item("[Subscription] Netflix", categoryId = SUBSCRIPTION_CATEGORY_ID))
        assertEquals(1, filterBudgetItems(list, emptyMap(), "netflix").size)
        // The stripped prefix is not searchable text any more, matching what the user sees.
        assertFalse(matchesQuery("subscription", budgetItemBaseTitle("[Subscription] Netflix")))
    }

    @Test
    fun `query is trimmed`() {
        val list = listOf(reminder("Dentist"))
        assertEquals(1, filterReminders(list, "  dentist  ").size)
    }
}
