package com.example.apextracker

import java.time.YearMonth
import java.util.Locale

/**
 * Spend-vs-cap state for one category in one month. Only built for categories that
 * actually have a cap — see [categoryLimitStatuses].
 */
data class CategoryLimitStatus(
    val category: Category,
    val spent: Double,
    val limit: Double,
    /** Spend as a share of the cap, clamped to 0..1 so it can drive a progress bar directly. */
    val fraction: Float,
    /** Cap minus spend; negative once the cap is blown, which is what the UI reports as "over". */
    val remaining: Double,
    val isOver: Boolean
)

/**
 * The cap to actually enforce, or null if this category is uncapped.
 *
 * Non-positive and non-finite caps normalize to null: a cap of 0 (or NaN, reachable only
 * from a corrupt cloud doc) has no meaningful progress — fraction would be a division by
 * zero — and "spend nothing" isn't a budget the UI can render. The category dialog stores
 * null for blank/zero input, so this only fires on data that got in some other way.
 */
fun Category.effectiveMonthlyLimit(): Double? =
    monthlyLimit?.takeIf { it > 0.0 && it.isFinite() }

/**
 * Turns the category dialog's free-text cap field into a stored [Category.monthlyLimit].
 *
 * Blank means "no cap", and so does 0 or an unparseable value — the field is optional, and
 * the same normalization as [effectiveMonthlyLimit] applies so a cap that would be ignored
 * at render time is never persisted in the first place.
 */
fun parseMonthlyLimitInput(raw: String): Double? =
    raw.trim().toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

/**
 * Renders a stored cap back into the dialog's text field.
 *
 * Deliberately not formatCurrency(): that produces "$400.00", which the field's numeric
 * input filter would reject on the next keystroke. Whole amounts drop the decimals so
 * editing a $400 cap doesn't start you at "400.0".
 */
fun formatLimitForInput(limit: Double): String =
    if (limit % 1.0 == 0.0) limit.toLong().toString()
    else String.format(Locale.US, "%.2f", limit)

/**
 * Spend-vs-cap for every capped category, for [month]'s items only.
 *
 * Ordered worst-first (over-limit, then by share of cap spent) so the tightest categories
 * surface at the top of the card without the user scrolling; ties break on name for a
 * stable order across recompositions.
 *
 * Note "at the cap exactly" is NOT over — spending your last budgeted dollar is on-budget.
 */
fun categoryLimitStatuses(
    items: List<BudgetItem>,
    categories: List<Category>,
    month: YearMonth
): List<CategoryLimitStatus> {
    val spentByCategory = items
        .filter { YearMonth.from(it.date) == month }
        .groupBy { it.categoryId }
        .mapValues { (_, catItems) -> catItems.sumOf { it.amount } }

    return categories
        .mapNotNull { category ->
            val limit = category.effectiveMonthlyLimit() ?: return@mapNotNull null
            val spent = spentByCategory[category.id] ?: 0.0
            CategoryLimitStatus(
                category = category,
                spent = spent,
                limit = limit,
                fraction = (spent / limit).toFloat().coerceIn(0f, 1f),
                remaining = limit - spent,
                isOver = spent > limit
            )
        }
        .sortedWith(
            compareByDescending<CategoryLimitStatus> { it.spent / it.limit }
                .thenBy { it.category.name }
        )
}
