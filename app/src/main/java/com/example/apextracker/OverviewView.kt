package com.example.apextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                    Text("DAILY INSIGHTS", 
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(if (showCalendar) Icons.Default.ViewAgenda else Icons.Default.CalendarMonth, 
                            contentDescription = "Toggle calendar view",
                            tint = if (showCalendar) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
        ) {
            if (showCalendar) {
                CalendarGrid(
                    selectedDate = selectedDate,
                    onDateSelected = { 
                        viewModel.selectDate(it)
                        showCalendar = false
                    }
                )
            } else {
                CompactDateNavigator(
                    selectedDate = selectedDate,
                    onDateSelected = { viewModel.selectDate(it) }
                )
                
                overview?.let { data ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // Stats Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard("Spent", "$${String.format("%.0f", data.totalSpent)}", Icons.Default.AccountBalanceWallet, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                                StatCard("Study", "${data.studyTimeMinutes}m", Icons.Default.Timer, MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f))
                                StatCard("Screen", "${data.screenTimeMinutes}m", Icons.Default.Monitor, MaterialTheme.colorScheme.tertiaryContainer, Modifier.weight(1f))
                            }
                        }

                        // Reminders Section
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("TASKS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        
                        if (data.missedReminders.isNotEmpty()) {
                            items(data.missedReminders) { reminder ->
                                ReminderSummaryCard(reminder, status = "Missed", onToggle = { viewModel.toggleReminder(it) })
                            }
                        }
                        
                        if (data.pendingReminders.isNotEmpty()) {
                            items(data.pendingReminders) { reminder ->
                                ReminderSummaryCard(reminder, status = "Pending", onToggle = { viewModel.toggleReminder(it) })
                            }
                        }

                        if (data.completedReminders.isNotEmpty()) {
                            items(data.completedReminders) { reminder ->
                                ReminderSummaryCard(reminder, status = "Completed", onToggle = { viewModel.toggleReminder(it) })
                            }
                        }

                        if (data.pendingReminders.isEmpty() && data.completedReminders.isEmpty() && data.missedReminders.isEmpty()) {
                            item { 
                                Surface(
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("All clear for today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactDateNavigator(selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onDateSelected(selectedDate.minusDays(1)) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Prev")
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM dd")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            IconButton(onClick = { onDateSelected(selectedDate.plusDays(1)) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next")
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, containerColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor.copy(alpha = 0.8f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun ReminderSummaryCard(reminder: Reminder, status: String, onToggle: (Reminder) -> Unit) {
    val (statusColor, statusBg) = when(status) {
        "Missed" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        "Completed" -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = statusBg,
        border = if (status == "Pending") androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = reminder.isCompleted, 
                onCheckedChange = { onToggle(reminder) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (reminder.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(12.dp), tint = statusColor)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reminder.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "No time",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            if (status == "Missed") {
                Text("MISSED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun CalendarGrid(selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val currentMonth = remember(selectedDate) { selectedDate.withDayOfMonth(1) }
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = currentMonth.dayOfWeek.value % 7 // 0 for Sunday

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(
            currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        
        // Days of week header
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(day, 
                    modifier = Modifier.weight(1f), 
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, 
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
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
                                .padding(2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onDateSelected(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isToday || isSelected) FontWeight.Black else FontWeight.Normal
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
