package com.example.apextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewView(onBackToMenu: () -> Unit, viewModel: OverviewViewModel = viewModel()) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val overview by viewModel.dayOverview.collectAsState()
    var showCalendar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Daily Overview", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (selectedDate == LocalDate.now()) "Today" 
                                   else selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(if (showCalendar) Icons.Default.ViewAgenda else Icons.Default.CalendarMonth, contentDescription = "Toggle View")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (showCalendar) {
                CalendarGrid(
                    selectedDate = selectedDate,
                    onDateSelected = { 
                        viewModel.selectDate(it)
                        showCalendar = false
                    }
                )
            } else {
                DateNavigator(
                    selectedDate = selectedDate,
                    onDateSelected = { viewModel.selectDate(it) }
                )
                
                overview?.let { data ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        // Reminders Section
                        item {
                            OverviewSectionHeader("Reminders", Icons.Default.Notifications)
                        }
                        
                        if (data.missedReminders.isNotEmpty()) {
                            item { Text("Missed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
                            items(data.missedReminders) { reminder ->
                                ReminderSummaryCard(reminder, isMissed = true, onToggle = { viewModel.toggleReminder(it) })
                            }
                        }
                        
                        if (data.pendingReminders.isNotEmpty()) {
                            item { Text("Due Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                            items(data.pendingReminders) { reminder ->
                                ReminderSummaryCard(reminder, isMissed = false, onToggle = { viewModel.toggleReminder(it) })
                            }
                        }

                        if (data.completedReminders.isNotEmpty()) {
                            item { Text("Completed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline) }
                            items(data.completedReminders) { reminder ->
                                ReminderSummaryCard(reminder, isMissed = false, onToggle = { viewModel.toggleReminder(it) })
                            }
                        }

                        if (data.pendingReminders.isEmpty() && data.completedReminders.isEmpty() && data.missedReminders.isEmpty()) {
                            item { Text("No reminders for this day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                        }

                        // Stats Section
                        item {
                            OverviewSectionHeader("Stats", Icons.Default.BarChart)
                        }
                        
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard("Spent", "$${String.format("%.2f", data.totalSpent)}", Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
                                StatCard("Study", "${data.studyTimeMinutes}m", Icons.Default.Timer, Modifier.weight(1f))
                                StatCard("Screen", "${data.screenTimeMinutes}m", Icons.Default.Monitor, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateNavigator(selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onDateSelected(selectedDate.minusDays(1)) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
        }
        Text(
            text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd")),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onDateSelected(selectedDate.plusDays(1)) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
        }
    }
}

@Composable
fun OverviewSectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
fun ReminderSummaryCard(reminder: Reminder, isMissed: Boolean, onToggle: (Reminder) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMissed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) 
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = reminder.isCompleted, onCheckedChange = { onToggle(reminder) })
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = reminder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (reminder.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )
                reminder.time?.let {
                    Text(it.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun CalendarGrid(selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val currentMonth = remember(selectedDate) { selectedDate.withDayOfMonth(1) }
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = currentMonth.dayOfWeek.value % 7 // 0 for Sunday

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Days of week header
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(day, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Days grid
        var day = 1
        for (i in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (j in 0 until 7) {
                    val currentDayIndex = i * 7 + j
                    if (currentDayIndex < firstDayOfWeek || day > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = currentMonth.withDayOfMonth(day)
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onDateSelected(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.toString(),
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        day++
                    }
                }
            }
            if (day > daysInMonth) break
        }
    }
}
