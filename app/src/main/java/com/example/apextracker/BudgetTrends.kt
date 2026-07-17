package com.example.apextracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.TextStyle

private const val MONTHS_BACK = 6
private val CHART_HEIGHT = 140.dp

/**
 * Total spend per month for the [monthsBack] months ending at [today] (inclusive), oldest
 * first. Months with no items still get an entry (amount 0.0) so the chart always has a
 * fixed, evenly-spaced set of bars.
 */
fun monthlyTotals(items: List<BudgetItem>, monthsBack: Int, today: YearMonth): List<Pair<YearMonth, Double>> {
    val sums = items.groupBy { YearMonth.from(it.date) }.mapValues { (_, v) -> v.sumOf { item -> item.amount } }
    return (monthsBack - 1 downTo 0).map { monthsAgo ->
        val month = today.minusMonths(monthsAgo.toLong())
        month to (sums[month] ?: 0.0)
    }
}

@Composable
fun BudgetTrendsCard(items: List<BudgetItem>, selectedMonth: YearMonth, onMonthSelected: (YearMonth) -> Unit) {
    val today = remember { YearMonth.now() }
    val totals = remember(items) { monthlyTotals(items, MONTHS_BACK, today) }
    val maxTotal = totals.maxOf { it.second }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.budget_trends_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (maxTotal == 0.0) {
                Box(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.budget_trends_empty), color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Row {
                    Column(
                        modifier = Modifier.height(CHART_HEIGHT).width(48.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        val currencyCode = LocalCurrencyCode.current
                        Text(formatCurrencyCompact(maxTotal, currencyCode), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(formatCurrencyCompact(maxTotal / 2, currencyCode), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(formatCurrencyCompact(0.0, currencyCode), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val mutedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    Row(
                        modifier = Modifier.weight(1f).height(CHART_HEIGHT),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        totals.forEach { (month, amount) ->
                            val heightFraction = (amount / maxTotal).toFloat()
                            val isCurrentMonth = month == today
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { onMonthSelected(month) },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .fillMaxHeight(heightFraction)
                                ) {
                                    drawRoundRect(
                                        color = if (isCurrentMonth) primaryColor else mutedColor,
                                        cornerRadius = CornerRadius(6.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val locale = LocalLocale.current.platformLocale
                Row(modifier = Modifier.padding(start = 60.dp)) {
                    totals.forEach { (month, _) ->
                        Text(
                            text = month.month.getDisplayName(TextStyle.SHORT, locale).take(1),
                            modifier = Modifier.weight(1f).clickable { onMonthSelected(month) },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (month == selectedMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
