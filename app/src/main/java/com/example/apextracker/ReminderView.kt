package com.example.apextracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderView(onBackToMenu: () -> Unit, viewModel: ReminderViewModel = viewModel()) {
    val activeReminders by viewModel.activeReminders.collectAsState(initial = emptyList())
    val completedReminders by viewModel.completedReminders.collectAsState(initial = emptyList())
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val allDayTime by viewModel.allDayNotificationTime.collectAsState(initial = LocalTime.NOON)
    val offset by viewModel.specificTimeOffsetMinutes.collectAsState(initial = 30)

    var showAddDialog by remember { mutableStateOf(false) }
    var showCompletedReminders by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val now = LocalDateTime.now()
    
    val sortedActiveReminders = remember(activeReminders, now) {
        activeReminders.sortedWith(compareByDescending<Reminder> { 
            it.isOverdue(now) 
        }.thenBy { it.date }.thenBy { it.time ?: LocalTime.MAX })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Reminder Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Reminder")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (sortedActiveReminders.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No active reminders", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                items(sortedActiveReminders) { reminder ->
                    ReminderItem(
                        reminder = reminder,
                        isOverdue = reminder.isOverdue(now),
                        onToggle = { viewModel.toggleCompletion(reminder) },
                        onDelete = { viewModel.deleteReminder(reminder) }
                    )
                }
            }

            if (completedReminders.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = { showCompletedReminders = !showCompletedReminders },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (showCompletedReminders) "Hide Completed" else "Show Completed (${completedReminders.size})")
                            Icon(
                                if (showCompletedReminders) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                    }
                }

                if (showCompletedReminders) {
                    items(completedReminders) { reminder ->
                        ReminderItem(
                            reminder = reminder,
                            isOverdue = false,
                            onToggle = { viewModel.toggleCompletion(reminder) },
                            onDelete = { viewModel.deleteReminder(reminder) }
                        )
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (showAddDialog) {
            AddReminderDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, date, time, description ->
                    viewModel.addReminder(name, date, time, description)
                    showAddDialog = false
                }
            )
        }

        if (showSettingsDialog) {
            ReminderSettingsDialog(
                enabled = notificationsEnabled,
                allDayTime = allDayTime,
                offset = offset,
                onDismiss = { showSettingsDialog = false },
                onToggleEnabled = { viewModel.setNotificationsEnabled(it) },
                onSetAllDayTime = { viewModel.setAllDayTime(it) },
                onSetOffset = { viewModel.setOffset(it) }
            )
        }
    }
}

@Composable
fun ReminderSettingsDialog(
    enabled: Boolean,
    allDayTime: LocalTime,
    offset: Int,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSetAllDayTime: (LocalTime) -> Unit,
    onSetOffset: (Int) -> Unit
) {
    val context = LocalContext.current
    val timePickerDialog = remember(allDayTime) {
        TimePickerDialog(context, { _, h, m -> onSetAllDayTime(LocalTime.of(h, m)) }, allDayTime.hour, allDayTime.minute, false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable Notifications", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = onToggleEnabled)
                }

                if (enabled) {
                    Divider()
                    Column {
                        Text("All-Day Reminder Time", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { timePickerDialog.show() }.padding(vertical = 8.dp)) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(allDayTime.format(DateTimeFormatter.ofPattern("hh:mm a")), style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    Column {
                        Text("Notification Lead Time", style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = offset.toFloat(),
                                onValueChange = { onSetOffset(it.toInt()) },
                                valueRange = 0f..120f,
                                steps = 11,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${offset}m", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Receive alert $offset minutes before task is due.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

fun Reminder.isOverdue(now: LocalDateTime): Boolean {
    if (isCompleted) return false
    val reminderDateTime = LocalDateTime.of(date, time ?: LocalTime.MAX)
    return reminderDateTime.isBefore(now)
}

@Composable
fun ReminderItem(
    reminder: Reminder,
    isOverdue: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = when {
            reminder.isCompleted -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            isOverdue -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
            else -> CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = reminder.isCompleted,
                onCheckedChange = { onToggle() },
                colors = if (isOverdue && !reminder.isCompleted) 
                    CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.error)
                    else CheckboxDefaults.colors()
            )
            
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = reminder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null,
                    color = if (isOverdue && !reminder.isCompleted) MaterialTheme.colorScheme.error else Color.Unspecified
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconTint = if (isOverdue && !reminder.isCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = iconTint
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reminder.date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        style = MaterialTheme.typography.bodySmall,
                        color = iconTint
                    )
                    
                    reminder.time?.let { time ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = iconTint
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = time.format(DateTimeFormatter.ofPattern("hh:mm a")),
                            style = MaterialTheme.typography.bodySmall,
                            color = iconTint
                        )
                    } ?: run {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All Day",
                            style = MaterialTheme.typography.bodySmall,
                            color = iconTint
                        )
                    }

                    if (isOverdue && !reminder.isCompleted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OVERDUE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                if (!reminder.description.isNullOrBlank()) {
                    Text(
                        text = reminder.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOverdue && !reminder.isCompleted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete, 
                    contentDescription = "Delete", 
                    tint = if (isOverdue && !reminder.isCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate, LocalTime?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var isAllDay by remember { mutableStateOf(true) }

    val context = LocalContext.current
    
    val datePickerDialog = remember(date) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                date = LocalDate.of(year, month + 1, dayOfMonth)
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth
        )
    }

    val timePickerDialog = remember(time) {
        val currentTime = LocalTime.now()
        val initialHour = time?.hour ?: currentTime.hour
        val initialMinute = time?.minute ?: currentTime.minute
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                time = LocalTime.of(hourOfDay, minute)
            },
            initialHour,
            initialMinute,
            false
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Reminder Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date: ${date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}")
                    Button(onClick = { datePickerDialog.show() }) {
                        Text("Pick Date")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isAllDay, onCheckedChange = { 
                        isAllDay = it
                        if (it) time = null
                    })
                    Text("All Day")
                }

                if (!isAllDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Time: ${time?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: "Select time"}")
                        Button(onClick = { timePickerDialog.show() }) {
                            Text("Pick Time")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, date, time, description.ifBlank { null })
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
