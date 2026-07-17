package com.example.apextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun BudgetCalendarView(
    items: List<BudgetItem>,
    categories: List<Category>,
    currentMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit
) {
    var selectedDayItems by remember { mutableStateOf<List<BudgetItem>?>(null) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value % 7
    val days = (1..daysInMonth).toList()
    val paddingDays = (0 until firstDayOfMonth).toList()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        BudgetMonthSelector(currentMonth = currentMonth, onMonthChange = onMonthChange)
        Spacer(modifier = Modifier.height(16.dp))
        WeekdayHeaders()
        Spacer(modifier = Modifier.height(8.dp))
        CalendarGrid(days, paddingDays, currentMonth, items, onDayClick = { date, dayItems ->
            selectedDate = date
            selectedDayItems = dayItems
        })
    }

    if (selectedDayItems != null && selectedDate != null) {
        DayBreakdownDialog(date = selectedDate!!, items = selectedDayItems!!, categories = categories, onDismiss = {
            selectedDayItems = null
            selectedDate = null
        })
    }
}

@Composable
fun BudgetMonthSelector(currentMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous_month))
        }
        Text(
            text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleLarge
        )
        IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_month))
        }
    }
}

@Composable
fun WeekdayHeaders() {
    val locale = LocalLocale.current.platformLocale
    Row(modifier = Modifier.fillMaxWidth()) {
        val firstDay = DayOfWeek.SUNDAY
        (0..6).map { firstDay.plus(it.toLong()) }.forEach { day ->
            Text(text = day.getDisplayName(TextStyle.SHORT, locale), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CalendarGrid(days: List<Int>, paddingDays: List<Int>, currentMonth: YearMonth, items: List<BudgetItem>, onDayClick: (LocalDate, List<BudgetItem>) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(paddingDays) { Box(modifier = Modifier.aspectRatio(1f)) }
        items(days) { day ->
            val date = currentMonth.atDay(day)
            val itemsForDay = items.filter { it.date == date }
            val totalSpent = itemsForDay.sumOf { it.amount }
            CalendarDayCard(day, date, totalSpent, onClick = { onDayClick(date, itemsForDay) })
        }
    }
}

@Composable
fun CalendarDayCard(day: Int, date: LocalDate, totalSpent: Double, onClick: () -> Unit) {
    Card(
        modifier = Modifier.aspectRatio(0.8f).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (date == LocalDate.now()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(2.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = day.toString(), style = MaterialTheme.typography.bodyMedium)
            if (totalSpent > 0) {
                Text(text = formatCurrency(totalSpent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun DayBreakdownDialog(date: LocalDate, items: List<BudgetItem>, categories: List<Category>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_breakdown_title, date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    val category = if (item.categoryId == -1L) {
                        Category(id = -1L, name = stringResource(R.string.budget_category_subscriptions), colorHex = "#FFD700")
                    } else {
                        categories.find { it.id == item.categoryId }
                    }
                    DayBreakdownItem(item, category)
                    HorizontalDivider()
                }
                TotalRow(items.sumOf { it.amount })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
fun DayBreakdownItem(item: BudgetItem, category: Category?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (category != null) {
                    Box(modifier = Modifier.size(8.dp).background(parseColorSafe(category.colorHex), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
            }
            if (!item.description.isNullOrBlank()) Text(text = item.description, style = MaterialTheme.typography.bodySmall)
        }
        Text(text = formatCurrency(item.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TotalRow(total: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = stringResource(R.string.budget_total), style = MaterialTheme.typography.titleMedium)
        Text(text = formatCurrency(total), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}