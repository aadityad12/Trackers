package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Issue #125 — the whole-month spending ceiling alongside the per-category caps. */
class OverallLimitTest {

    private val july = YearMonth.of(2026, 7)

    private fun item(day: Int, amount: Double, month: YearMonth = july, categoryId: Long? = null) =
        BudgetItem(
            title = "x",
            amount = amount,
            date = month.atDay(day),
            categoryId = categoryId
        )

    @Test
    fun `no ceiling means no status`() {
        assertNull(overallLimitStatus(listOf(item(1, 10.0)), july, null))
        assertNull(overallLimitStatus(listOf(item(1, 10.0)), july, 0.0))
        assertNull(overallLimitStatus(listOf(item(1, 10.0)), july, Double.NaN))
    }

    @Test
    fun `counts every category including uncategorized and subscriptions`() {
        val items = listOf(
            item(1, 10.0, categoryId = 5L),
            item(2, 15.0, categoryId = null),
            item(3, 25.0, categoryId = SUBSCRIPTION_CATEGORY_ID)
        )
        val status = overallLimitStatus(items, july, 100.0)!!
        assertEquals(50.0, status.spent, 0.001)
        assertEquals(50.0, status.remaining, 0.001)
        assertEquals(0.5f, status.fraction, 0.001f)
        assertFalse(status.isOver)
    }

    @Test
    fun `other months are excluded`() {
        val items = listOf(item(1, 10.0), item(1, 999.0, month = YearMonth.of(2026, 6)))
        assertEquals(10.0, overallLimitStatus(items, july, 100.0)!!.spent, 0.001)
    }

    @Test
    fun `exactly at the ceiling is not over, a penny past is`() {
        assertFalse(overallLimitStatus(listOf(item(1, 100.0)), july, 100.0)!!.isOver)
        val over = overallLimitStatus(listOf(item(1, 100.01)), july, 100.0)!!
        assertTrue(over.isOver)
        assertTrue(over.remaining < 0)
        assertEquals(1f, over.fraction, 0.001f) // clamped for the progress bar
    }

    @Test
    fun `empty month reports zero spent`() {
        val status = overallLimitStatus(emptyList<BudgetItem>(), july, 200.0)!!
        assertEquals(0.0, status.spent, 0.001)
        assertEquals(200.0, status.remaining, 0.001)
    }
}
