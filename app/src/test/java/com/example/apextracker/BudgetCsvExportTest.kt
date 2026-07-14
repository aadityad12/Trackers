package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BudgetCsvExportTest {

    @Test
    fun `plain field is not quoted`() {
        assertEquals("Coffee", csvEscape("Coffee"))
    }

    @Test
    fun `field with a comma is quoted`() {
        assertEquals("\"Coffee, tea\"", csvEscape("Coffee, tea"))
    }

    @Test
    fun `field with a quote is quoted and the quote doubled`() {
        assertEquals("\"Bob\"\"s coffee\"", csvEscape("Bob\"s coffee"))
    }

    @Test
    fun `field with a comma and a quote is quoted with the quote doubled`() {
        assertEquals("\"Bob\"\"s, coffee\"", csvEscape("Bob\"s, coffee"))
    }

    @Test
    fun `field with a newline is quoted`() {
        assertEquals("\"line1\nline2\"", csvEscape("line1\nline2"))
    }

    @Test
    fun `resolveCategoryName maps the subscriptions sentinel`() {
        assertEquals("Subscriptions", resolveCategoryName(-1L, emptyList()))
    }

    @Test
    fun `resolveCategoryName is blank for a null categoryId`() {
        assertEquals("", resolveCategoryName(null, emptyList()))
    }

    @Test
    fun `resolveCategoryName looks up a real category`() {
        val categories = listOf(Category(id = 5, name = "Groceries", colorHex = "#000000"))
        assertEquals("Groceries", resolveCategoryName(5L, categories))
    }

    @Test
    fun `resolveCategoryName is blank when the category no longer exists`() {
        assertEquals("", resolveCategoryName(99L, emptyList()))
    }

    @Test
    fun `buildBudgetCsv has the expected header`() {
        val csv = buildBudgetCsv(emptyList(), emptyList())
        assertEquals("date,title,amount,category,description", csv)
    }

    @Test
    fun `buildBudgetCsv writes one row per item with correct column order`() {
        val categories = listOf(Category(id = 1, name = "Food", colorHex = "#000000"))
        val items = listOf(
            BudgetItem(title = "Lunch", amount = 12.5, description = "with friends", date = LocalDate.of(2026, 7, 13), categoryId = 1L)
        )
        val csv = buildBudgetCsv(items, categories)
        assertEquals(
            "date,title,amount,category,description\n2026-07-13,Lunch,12.5,Food,with friends",
            csv
        )
    }

    @Test
    fun `buildBudgetCsv quotes a title containing a comma and a quote`() {
        val items = listOf(
            BudgetItem(title = "Bob\"s, lunch", amount = 5.0, date = LocalDate.of(2026, 1, 1), categoryId = null)
        )
        val csv = buildBudgetCsv(items, emptyList())
        assertEquals(
            "date,title,amount,category,description\n2026-01-01,\"Bob\"\"s, lunch\",5.0,,",
            csv
        )
    }

    @Test
    fun `buildBudgetCsv leaves description blank when null`() {
        val items = listOf(
            BudgetItem(title = "Item", amount = 1.0, description = null, date = LocalDate.of(2026, 1, 1), categoryId = null)
        )
        val csv = buildBudgetCsv(items, emptyList())
        assertEquals(
            "date,title,amount,category,description\n2026-01-01,Item,1.0,,",
            csv
        )
    }
}
