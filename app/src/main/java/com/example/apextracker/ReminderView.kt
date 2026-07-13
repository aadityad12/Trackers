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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
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
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    // Exact-alarm permission is denied by default on API 33+; without it reminders fire
    // inexactly (possibly hours late). Track it across resumes so returning from the system
    // grant screen updates the banner and re-arms alarms exactly.
    val context = LocalContext.current
    var canScheduleExact by remember { mutableStateOf(ReminderScheduler.canScheduleExactAlarms(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ReminderScheduler.canScheduleExactAlarms(context)
                if (granted && !canScheduleExact) viewModel.rescheduleAll()
                canScheduleExact = granted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                        Text(stringResource(R.string.reminders_selected_count, selectedCompletedIds.size), style = MaterialTheme.typography.titleSmall)
                    } else {
                        Text(stringResource(R.string.reminders_title), 
                            style = MaterialTheme.typography.titleSmall
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            if (notificationsEnabled && !canScheduleExact) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.reminders_late_banner_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.reminders_late_banner_text),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                ReminderScheduler.requestExactAlarmIntent(context)?.let { context.startActivity(it) }
                            }) {
                                Text(stringResource(R.string.reminders_allow), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.reminders_active), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }

            if (sortedActiveReminders.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.reminders_all_done), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
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
                        Text(stringResource(R.string.reminders_completed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                        
                        TextButton(
                            onClick = { showCompletedReminders = !showCompletedReminders },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (showCompletedReminders) stringResource(R.string.reminders_hide) else stringResource(R.string.reminders_show_count, completedReminders.size), style = MaterialTheme.typography.labelSmall)
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
                                Text(stringResource(R.string.reminders_clear_completed), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            ReminderEditDialog(
                title = stringResource(R.string.reminders_new_title),
                onDismiss = { showAddDialog = false },
                onConfirm = { name, date, time, description, recurrence ->
                    viewModel.addReminder(name, date, time, description, recurrence)
                    showAddDialog = false
                }
            )
        }

        if (reminderToEdit != null) {
            ReminderEditDialog(
                title = stringResource(R.string.reminders_edit_title),
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
                    val deleted = reminderToEdit!!
                    viewModel.deleteReminder(deleted)
                    reminderToEdit = null
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.deleted_quoted, deleted.name),
                            actionLabel = resources.getString(R.string.action_undo),
                            duration = SnackbarDuration.Short
                        )
                        // The cloud delete has already been pushed; undoing re-pushes
                        // the same cloudId, which recreates the doc (and re-arms the alarm).
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restoreReminder(deleted)
                        }
                    }
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
                title = { Text(stringResource(R.string.reminders_clear_confirm_title)) },
                text = { Text(stringResource(R.string.reminders_clear_confirm_text)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllCompleted()
                            showClearAllConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
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
                    label = { Text(stringResource(R.string.label_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.label_description_optional)) },
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
                        Text(stringResource(R.string.reminders_set_all_day))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.reminders_recurrence_prefix, recurrence?.frequency?.name ?: stringResource(R.string.reminders_recurrence_none)), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showRecurrencePicker = true }) {
                        Text(if (recurrence == null) "Set" else "Change")
                    }
                }
                if (recurrence != null) {
                    TextButton(onClick = { recurrence = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.reminders_remove_recurrence))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, date, time, description, recurrence) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )

    if (showRecurrencePicker) {
        RecurrencePickerDialog(
            initialRecurrence = recurrence,
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
        title = { Text(stringResource(R.string.reminders_settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminders_enable_notifications), modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = onToggleEnabled)
                }
                
                Column {
                    Text(stringResource(R.string.reminders_all_day_time), style = MaterialTheme.typography.labelMedium)
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
                    Text(stringResource(R.string.reminders_offset_label), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = offset.toFloat(),
                        onValueChange = { onSetOffset(it.toInt()) },
                        valueRange = 0f..120f,
                        steps = 23
                    )
                    Text(stringResource(R.string.reminders_minutes_before, offset), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
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
                            text = stringResource(R.string.reminders_overdue),
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
