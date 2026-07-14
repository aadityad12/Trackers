package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetTrendsTest {

    private val july = YearMonth.of(2026, 7)

    private fun item(amount: Double, date: LocalDate) = BudgetItem(title = "x", amount = amount, date = date)

    @Test
    fun `returns one entry per month in range, oldest first`() {
        val totals = monthlyTotals(emptyList(), monthsBack = 3, today = july)
        assertEquals(
            listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7)),
            totals.map { it.first }
        )
    }

    @Test
    fun `months with no items default to zero`() {
        val totals = monthlyTotals(emptyList(), monthsBack = 3, today = july)
        assertEquals(listOf(0.0, 0.0, 0.0), totals.map { it.second })
    }

    @Test
    fun `sums items within the same month`() {
        val items = listOf(
            item(10.0, LocalDate.of(2026, 7, 1)),
            item(5.5, LocalDate.of(2026, 7, 15))
        )
        val totals = monthlyTotals(items, monthsBack = 1, today = july)
        assertEquals(15.5, totals.single().second, 0.0001)
    }

    @Test
    fun `keeps months separate`() {
        val items = listOf(
            item(10.0, LocalDate.of(2026, 6, 1)),
            item(20.0, LocalDate.of(2026, 7, 1))
        )
        val totals = monthlyTotals(items, monthsBack = 2, today = july)
        assertEquals(mapOf(YearMonth.of(2026, 6) to 10.0, YearMonth.of(2026, 7) to 20.0), totals.toMap())
    }

    @Test
    fun `ignores items outside the requested range`() {
        val items = listOf(item(100.0, LocalDate.of(2025, 1, 1)))
        val totals = monthlyTotals(items, monthsBack = 3, today = july)
        assertEquals(listOf(0.0, 0.0, 0.0), totals.map { it.second })
    }

    @Test
    fun `monthsBack of 1 returns only the current month`() {
        val totals = monthlyTotals(emptyList(), monthsBack = 1, today = july)
        assertEquals(listOf(july), totals.map { it.first })
    }
}
