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
fun BudgetLimitsCard(items: List<BudgetItem>, categories: List<Category>, month: YearMonth) {
    val statuses = remember(items, categories, month) { categoryLimitStatuses(items, categories, month) }
    if (statuses.isEmpty()) return

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
    val barColor = if (status.isOver) {
        MaterialTheme.colorScheme.error
    } else {
        parseColorSafe(status.category.colorHex)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = status.category.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    R.string.budget_limit_spent_of,
                    formatCurrency(status.spent, LocalCurrencyCode.current),
                    formatCurrency(status.limit, LocalCurrencyCode.current)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            progress = { status.fraction },
            modifier = Modifier.fillMaxWidth().height(BAR_HEIGHT),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )

        Text(
            text = if (status.isOver) {
                // remaining is negative once over; the string already says "over".
                stringResource(R.string.budget_limit_over_by, formatCurrency(abs(status.remaining), LocalCurrencyCode.current))
            } else {
                stringResource(R.string.budget_limit_remaining, formatCurrency(status.remaining, LocalCurrencyCode.current))
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (status.isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
        )
    }
}
