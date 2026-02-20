package com.example.apextracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReminderView(onBackToMenu: () -> Unit, viewModel: ReminderViewModel = viewModel()) {
    val activeReminders by viewModel.activeReminders.collectAsState(initial = emptyList())
    val completedReminders by viewModel.completedReminders.collectAsState(initial = emptyList())
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val allDayTime by viewModel.allDayNotificationTime.collectAsState(initial = LocalTime.NOON)
    val offset by viewModel.specificTimeOffsetMinutes.collectAsState(initial = 30)

    var showAddDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<Reminder?>(null) }
    var showCompletedReminders by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    
    var selectedCompletedIds by remember { mutableStateOf(setOf<Long>()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    val now = LocalDateTime.now()
    
    val sortedActiveReminders = remember(activeReminders, now) {
        activeReminders.sortedWith(compareByDescending<Reminder> { 
            it.isOverdue(now) 
        }.thenBy { it.date }.thenBy { it.time ?: LocalTime.MAX })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text("${selectedCompletedIds.size} SELECTED", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    } else {
                        Text("TASK LIST", 
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { 
                            isSelectionMode = false
                            selectedCompletedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    } else {
                        IconButton(onClick = onBackToMenu) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            viewModel.deleteReminders(selectedCompletedIds.toList())
                            isSelectionMode = false
                            selectedCompletedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    } else {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { 
                Spacer(modifier = Modifier.height(8.dp))
                Text("ACTIVE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            if (sortedActiveReminders.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("All tasks completed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            } else {
                items(sortedActiveReminders) { reminder ->
                    ReminderItemModern(
                        reminder = reminder,
                        isOverdue = reminder.isOverdue(now),
                        onToggle = { viewModel.toggleCompletion(reminder) },
                        onEdit = { reminderToEdit = reminder }
                    )
                }
            }

            if (completedReminders.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("COMPLETED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                        
                        TextButton(
                            onClick = { showCompletedReminders = !showCompletedReminders },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (showCompletedReminders) "HIDE" else "SHOW (${completedReminders.size})", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (showCompletedReminders) {
                    items(completedReminders) { reminder ->
                        ReminderItemModern(
                            reminder = reminder,
                            isOverdue = false,
                            onToggle = { viewModel.toggleCompletion(reminder) },
                            onEdit = { /* No editing for completed */ },
                            isSelected = selectedCompletedIds.contains(reminder.id),
                            isSelectionMode = isSelectionMode,
                            onLongClick = {
                                isSelectionMode = true
                                selectedCompletedIds = selectedCompletedIds + reminder.id
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedCompletedIds = if (selectedCompletedIds.contains(reminder.id)) {
                                        selectedCompletedIds - reminder.id
                                    } else {
                                        selectedCompletedIds + reminder.id
                                    }
                                    if (selectedCompletedIds.isEmpty()) isSelectionMode = false
                                }
                            }
                        )
                    }
                    
                    item {
                        if (!isSelectionMode) {
                            TextButton(
                                onClick = { showClearAllConfirm = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Clear All Completed", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            ReminderEditDialog(
                title = "New Reminder",
                onDismiss = { showAddDialog = false },
                onConfirm = { name, date, time, description, recurrence ->
                    viewModel.addReminder(name, date, time, description, recurrence)
                    showAddDialog = false
                }
            )
        }

        if (reminderToEdit != null) {
            ReminderEditDialog(
                title = "Edit Reminder",
                initialName = reminderToEdit!!.name,
                initialDescription = reminderToEdit!!.description ?: "",
                initialDate = reminderToEdit!!.date,
                initialTime = reminderToEdit!!.time,
                initialRecurrence = reminderToEdit!!.recurrence,
                onDismiss = { reminderToEdit = null },
                onConfirm = { name, date, time, description, recurrence ->
                    viewModel.updateReminder(reminderToEdit!!.copy(
                        name = name,
                        date = date,
                        time = time,
                        description = description,
                        recurrence = recurrence
                    ))
                    reminderToEdit = null
                },
                onDelete = {
                    viewModel.deleteReminder(reminderToEdit!!)
                    reminderToEdit = null
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

        if (showClearAllConfirm) {
            AlertDialog(
                onDismissRequest = { showClearAllConfirm = false },
                title = { Text("Clear Completed?") },
                text = { Text("Permanently delete all completed tasks?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllCompleted()
                            showClearAllConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ReminderEditDialog(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    initialDate: LocalDate = LocalDate.now(),
    initialTime: LocalTime? = null,
    initialRecurrence: Recurrence? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate, LocalTime?, String, Recurrence?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf(initialTime) }
    var recurrence by remember { mutableStateOf(initialRecurrence) }
    
    var showRecurrencePicker by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            DatePickerDialog(context, { _, year, month, day ->
                                date = LocalDate.of(year, month + 1, day)
                            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
                    }
                    
                    Button(
                        onClick = {
                            val t = time ?: LocalTime.now()
                            TimePickerDialog(context, { _, hour, minute ->
                                time = LocalTime.of(hour, minute)
                            }, t.hour, t.minute, true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "All Day")
                    }
                }
                
                if (time != null) {
                    TextButton(onClick = { time = null }) {
                        Text("Set as All Day")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recurrence: ${recurrence?.frequency?.name ?: "None"}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showRecurrencePicker = true }) {
                        Text(if (recurrence == null) "Set" else "Change")
                    }
                }
                if (recurrence != null) {
                    TextButton(onClick = { recurrence = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Remove Recurrence")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, date, time, description, recurrence) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )

    if (showRecurrencePicker) {
        RecurrencePickerDialog(
            onDismiss = { showRecurrencePicker = false },
            onConfirm = { 
                recurrence = it
                showRecurrencePicker = false
            }
        )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Notifications", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = onToggleEnabled)
                }
                
                Column {
                    Text("All-day reminders time", style = MaterialTheme.typography.labelMedium)
                    Button(
                        onClick = {
                            TimePickerDialog(context, { _, hour, minute ->
                                onSetAllDayTime(LocalTime.of(hour, minute))
                            }, allDayTime.hour, allDayTime.minute, true).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(allDayTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }

                Column {
                    Text("Specific time offset (minutes)", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = offset.toFloat(),
                        onValueChange = { onSetOffset(it.toInt()) },
                        valueRange = 0f..120f,
                        steps = 23
                    )
                    Text("${offset} minutes before", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderItemModern(
    reminder: Reminder,
    isOverdue: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit = {},
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        reminder.isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { 
                    if (isSelectionMode) onClick() 
                    else if (!reminder.isCompleted) onEdit() 
                },
                onLongClick = { if (reminder.isCompleted) onLongClick() }
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                 else if (isOverdue && !reminder.isCompleted) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                 else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = reminder.isCompleted,
                onCheckedChange = { if (!isSelectionMode) onToggle() else onClick() },
                colors = if (isOverdue && !reminder.isCompleted) 
                    CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.error)
                    else CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
            
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reminder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else null,
                        color = when {
                            reminder.isCompleted -> MaterialTheme.colorScheme.outline
                            isOverdue -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (reminder.recurrence != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recurring",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (isOverdue && !reminder.isCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (reminder.date == LocalDate.now()) "Today" else reminder.date.format(DateTimeFormatter.ofPattern("MMM dd")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    reminder.time?.let { time ->
                        Text(
                            text = " • ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (isOverdue && !reminder.isCompleted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OVERDUE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            if (!reminder.isCompleted && !isSelectionMode) {
                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight, 
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
