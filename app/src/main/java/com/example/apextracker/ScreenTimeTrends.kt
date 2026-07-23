package com.example.apextracker

import androidx.compose.foundation.Canvas
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
import java.time.LocalDate
import java.time.format.TextStyle

private const val DAYS_BACK = 7
private val CHART_HEIGHT = 140.dp

/**
 * Total screen time per day for the [days] days ending at [today] (inclusive), oldest first.
 * Days with no recorded session still get an entry (0L) so the chart always has a fixed,
 * evenly-spaced set of bars; multiple sessions on the same day are summed (defensive — the
 * table's primary key is the date, so at most one row exists per day today). Days outside the
 * window are ignored. Mirrors BudgetTrends.monthlyTotals.
 */
fun dailyTotals(sessions: List<ScreenTimeSession>, days: Int, today: LocalDate): List<Pair<LocalDate, Long>> {
    val sums = sessions.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { s -> s.durationMillis } }
    return (days - 1 downTo 0).map { daysAgo ->
        val day = today.minusDays(daysAgo.toLong())
        day to (sums[day] ?: 0L)
    }
}

/**
 * Hand-drawn 7-day bar chart of this device's daily screen time (today rightmost, highlighted),
 * consistent with BudgetTrendsCard / ApexLogo (bare Canvas, no chart library). Purely
 * presentational: it draws from the already-computed [sessions] state, never triggering usage
 * recomputation. Normalizing to the tallest bar keeps a single inflated historical day (Issue #89)
 * from crashing or emptying the chart — it just reads as a full bar.
 */
@Composable
fun ScreenTimeTrendsCard(sessions: List<ScreenTimeSession>) {
    val today = remember { LocalDate.now() }
    val totals = remember(sessions) { dailyTotals(sessions, DAYS_BACK, today) }
    val maxTotal = totals.maxOf { it.second }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.screen_trends_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (maxTotal == 0L) {
                Box(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.screen_trends_empty), color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Row {
                    Column(
                        modifier = Modifier.height(CHART_HEIGHT).width(48.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        // One unit for all three labels, chosen from the max — a sub-minute
                        // busiest day used to collapse to three "0m"s (Issue #97).
                        durationAxisLabels(maxTotal).forEach { label ->
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val mutedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    Row(
                        modifier = Modifier.weight(1f).height(CHART_HEIGHT),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        totals.forEach { (day, millis) ->
                            val heightFraction = (millis.toDouble() / maxTotal).toFloat()
                            val isToday = day == today
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .fillMaxHeight(heightFraction)
                                ) {
                                    drawRoundRect(
                                        color = if (isToday) primaryColor else mutedColor,
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
                    totals.forEach { (day, _) ->
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(1),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
