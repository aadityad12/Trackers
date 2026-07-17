package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CategoryLimitsTest {

    private val july = YearMonth.of(2026, 7)

    private fun category(id: Long, name: String = "Groceries", limit: Double? = null) =
        Category(id = id, name = name, colorHex = "#4986e7", monthlyLimit = limit)

    private fun item(amount: Double, categoryId: Long?, date: LocalDate = LocalDate.of(2026, 7, 10)) =
        BudgetItem(title = "x", amount = amount, date = date, categoryId = categoryId)

    // ── effectiveMonthlyLimit ────────────────────────────────────────────────

    @Test
    fun `null limit is uncapped`() {
        assertNull(category(1, limit = null).effectiveMonthlyLimit())
    }

    @Test
    fun `zero limit normalizes to uncapped`() {
        assertNull(category(1, limit = 0.0).effectiveMonthlyLimit())
    }

    @Test
    fun `negative limit normalizes to uncapped`() {
        assertNull(category(1, limit = -50.0).effectiveMonthlyLimit())
    }

    @Test
    fun `non-finite limit normalizes to uncapped`() {
        assertNull(category(1, limit = Double.NaN).effectiveMonthlyLimit())
        assertNull(category(1, limit = Double.POSITIVE_INFINITY).effectiveMonthlyLimit())
    }

    @Test
    fun `positive limit is kept`() {
        assertEquals(400.0, category(1, limit = 400.0).effectiveMonthlyLimit()!!, 0.0001)
    }

    // ── categoryLimitStatuses ────────────────────────────────────────────────

    @Test
    fun `uncapped categories are excluded entirely`() {
        val statuses = categoryLimitStatuses(
            items = listOf(item(100.0, 1L)),
            categories = listOf(category(1, limit = null)),
            month = july
        )
        assertTrue(statuses.isEmpty())
    }

    @Test
    fun `zero limit category is excluded rather than dividing by zero`() {
        val statuses = categoryLimitStatuses(
            items = listOf(item(100.0, 1L)),
            categories = listOf(category(1, limit = 0.0)),
            month = july
        )
        assertTrue(statuses.isEmpty())
    }

    @Test
    fun `capped category with no spending reports zero`() {
        val status = categoryLimitStatuses(
            items = emptyList(),
            categories = listOf(category(1, limit = 400.0)),
            month = july
        ).single()

        assertEquals(0.0, status.spent, 0.0001)
        assertEquals(0f, status.fraction, 0.0001f)
        assertEquals(400.0, status.remaining, 0.0001)
        assertFalse(status.isOver)
    }

    @Test
    fun `spending exactly at the limit is not over`() {
        val status = categoryLimitStatuses(
            items = listOf(item(400.0, 1L)),
            categories = listOf(category(1, limit = 400.0)),
            month = july
        ).single()

        assertEquals(1f, status.fraction, 0.0001f)
        assertEquals(0.0, status.remaining, 0.0001)
        assertFalse(status.isOver)
    }

    @Test
    fun `spending one cent over the limit is over`() {
        val status = categoryLimitStatuses(
            items = listOf(item(400.01, 1L)),
            categories = listOf(category(1, limit = 400.0)),
            month = july
        ).single()

        assertTrue(status.isOver)
        assertEquals(-0.01, status.remaining, 0.0001)
    }

    @Test
    fun `fraction clamps at one when far over the limit`() {
        val status = categoryLimitStatuses(
            items = listOf(item(1200.0, 1L)),
            categories = listOf(category(1, limit = 400.0)),
            month = july
        ).single()

        assertEquals(1f, status.fraction, 0.0001f)
        assertEquals(1200.0, status.spent, 0.0001)
        assertEquals(-800.0, status.remaining, 0.0001)
        assertTrue(status.isOver)
    }

    @Test
    fun `sums multiple items in the same category`() {
        val status = categoryLimitStatuses(
            items = listOf(item(100.0, 1L), item(50.5, 1L)),
            categories = listOf(category(1, limit = 400.0)),
            month = july
        ).single()

        assertEquals(150.5, status.spent, 0.0001)
    }

    @Test
    fun `items from other months do not count`() {
        val status = categoryLimitStatuses(
            items = listOf(
                item(100.0, 1L, LocalDate.of(2026, 6, 30)),
                item(25.0, 1L, LocalDate.of(2026, 7, 1)),
                item(100.0, 1L, LocalDate.of(2026, 8, 1))
            ),
            categories = listOf(category(1, limit = 400.0)),
            month = july
        ).single()

        assertEquals(25.0, status.spent, 0.0001)
    }

    @Test
    fun `items from other categories do not count`() {
        val status = categoryLimitStatuses(
            items = listOf(item(100.0, 2L), item(30.0, 1L), item(70.0, null)),
            categories = listOf(category(1, limit = 400.0)),
            month = july
        ).single()

        assertEquals(30.0, status.spent, 0.0001)
    }

    @Test
    fun `orders by share of limit spent, worst first`() {
        val statuses = categoryLimitStatuses(
            items = listOf(item(50.0, 1L), item(90.0, 2L), item(200.0, 3L)),
            categories = listOf(
                category(1, name = "Fuel", limit = 100.0),      // 50%
                category(2, name = "Coffee", limit = 100.0),    // 90%
                category(3, name = "Rent", limit = 100.0)       // 200% — over
            ),
            month = july
        )

        assertEquals(listOf("Rent", "Coffee", "Fuel"), statuses.map { it.category.name })
    }

    @Test
    fun `ties break on category name for a stable order`() {
        val statuses = categoryLimitStatuses(
            items = listOf(item(50.0, 1L), item(50.0, 2L)),
            categories = listOf(
                category(2, name = "Zebra", limit = 100.0),
                category(1, name = "Apple", limit = 100.0)
            ),
            month = july
        )

        assertEquals(listOf("Apple", "Zebra"), statuses.map { it.category.name })
    }

    @Test
    fun `mixes capped and uncapped categories, returning only the capped ones`() {
        val statuses = categoryLimitStatuses(
            items = listOf(item(50.0, 1L), item(500.0, 2L)),
            categories = listOf(
                category(1, name = "Fuel", limit = 100.0),
                category(2, name = "Rent", limit = null)
            ),
            month = july
        )

        assertEquals(listOf("Fuel"), statuses.map { it.category.name })
    }
}
