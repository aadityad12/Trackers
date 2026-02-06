package com.example.apextracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderView(onBackToMenu: () -> Unit, viewModel: ReminderViewModel = viewModel()) {
    val activeReminders by viewModel.activeReminders.collectAsState(initial = emptyList())
    val completedReminders by viewModel.completedReminders.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var showCompletedReminders by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            if (activeReminders.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No active reminders", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                items(activeReminders) { reminder ->
                    ReminderItem(
                        reminder = reminder,
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
    }
}

@Composable
fun ReminderItem(
    reminder: Reminder,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (reminder.isCompleted) 
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = reminder.isCompleted,
                onCheckedChange = { onToggle() }
            )
            
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = reminder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reminder.date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    reminder.time?.let { time ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = time.format(DateTimeFormatter.ofPattern("hh:mm a")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } ?: run {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All Day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                if (!reminder.description.isNullOrBlank()) {
                    Text(
                        text = reminder.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
