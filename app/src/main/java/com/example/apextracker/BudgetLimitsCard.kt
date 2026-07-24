package com.example.apextracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import kotlin.math.abs

private val BAR_HEIGHT = 8.dp

/**
 * Spend-vs-cap progress for the capped categories in [month].
 *
 * Renders nothing at all when no category has a cap — limits are opt-in, and an empty
 * card would be permanent noise for users who never set one.
 */
@Composable
fun BudgetLimitsCard(
    items: List<BudgetItem>,
    categories: List<Category>,
    month: YearMonth,
    overallLimit: Double? = null
) {
    val statuses = remember(items, categories, month) { categoryLimitStatuses(items, categories, month) }
    val overall = remember(items, month, overallLimit) { overallLimitStatus(items, month, overallLimit) }
    if (statuses.isEmpty() && overall == null) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.budget_category_limits_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            // The whole-month ceiling reads first — it's the number the per-category rows add up
            // against (Issue #125).
            overall?.let { OverallLimitRow(it) }
            statuses.forEach { status ->
                CategoryLimitRow(status)
            }
        }
    }
}

@Composable
private fun CategoryLimitRow(status: CategoryLimitStatus) {
    // Over-limit switches to the theme's error color; under-limit keeps the category's own
    // color so the row still reads as that category at a glance.
    LimitRow(
        label = status.category.name,
        spent = status.spent,
        limit = status.limit,
        fraction = status.fraction,
        remaining = status.remaining,
        isOver = status.isOver,
        barColor = if (status.isOver) MaterialTheme.colorScheme.error else parseColorSafe(status.category.colorHex)
    )
}

@Composable
private fun LimitRow(
    label: String,
    spent: Double,
    limit: Double,
    fraction: Float,
    remaining: Double,
    isOver: Boolean,
    barColor: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    R.string.budget_limit_spent_of,
                    formatCurrency(spent, LocalCurrencyCode.current),
                    formatCurrency(limit, LocalCurrencyCode.current)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )

        Text(
            text = if (isOver) {
                // remaining is negative once over; the string already says "over".
                stringResource(R.string.budget_limit_over_by, formatCurrency(abs(remaining), LocalCurrencyCode.current))
            } else {
                stringResource(R.string.budget_limit_remaining, formatCurrency(remaining, LocalCurrencyCode.current))
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
        )
    }
}

/** The overall monthly ceiling, styled like a category row but named and coloured for the total. */
@Composable
private fun OverallLimitRow(status: OverallLimitStatus) {
    LimitRow(
        label = stringResource(R.string.budget_overall_limit_label_row),
        spent = status.spent,
        limit = status.limit,
        fraction = status.fraction,
        remaining = status.remaining,
        isOver = status.isOver,
        barColor = if (status.isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    )
}
