package com.example.apextracker

import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import android.app.DatePickerDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyTrackerView(onBackToMenu: () -> Unit, viewModel: StudyViewModel = viewModel()) {
    val timeSeconds by viewModel.timeSeconds.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentSubject by viewModel.currentSubject.collectAsState()
    val allSessions by viewModel.getAllSessions().collectAsState(initial = emptyList())
    val dailyGoalMinutes by viewModel.dailyGoalMinutes.collectAsState()
    val todayTotalSeconds by viewModel.todayTotalSeconds.collectAsState()
    val studyStreak by viewModel.studyStreak.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.handleAppBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pastDays = remember(allSessions) {
        val today = LocalDate.now()
        groupSessionsByDate(allSessions.filter { it.date.isBefore(today) })
    }
    val knownSubjects = remember(allSessions) { knownSubjects(allSessions) }

    var showResetConfirm by remember { mutableStateOf(false) }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.study_reset_confirm_title)) },
            text = { Text(stringResource(R.string.study_reset_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetTimerManual()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Manual backfill of a missed past session (Issue #122). Non-null while the dialog is open;
    // the value seeds the date/subject/duration fields (an existing row when opened from history).
    var manualEntry by remember { mutableStateOf<ManualSessionSeed?>(null) }
    manualEntry?.let { seed ->
        ManualSessionDialog(
            seed = seed,
            knownSubjects = knownSubjects,
            onDismiss = { manualEntry = null },
            onSave = { date, subject, seconds ->
                viewModel.logManualSession(date, subject, seconds)
                manualEntry = null
            }
        )
    }

    var showGoalDialog by remember { mutableStateOf(false) }
    if (showGoalDialog) {
        StudyGoalDialog(
            currentMinutes = dailyGoalMinutes,
            onDismiss = { showGoalDialog = false },
            onSave = { viewModel.setDailyGoalMinutes(it); showGoalDialog = false }
        )
    }

    var showSubjectPicker by remember { mutableStateOf(false) }
    if (showSubjectPicker) {
        SubjectPickerDialog(
            current = currentSubject,
            knownSubjects = knownSubjects,
            onDismiss = { showSubjectPicker = false },
            onSelect = {
                viewModel.selectSubject(it)
                showSubjectPicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.study_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showGoalDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.study_goal_setting))
                    }
                    IconButton(onClick = { showResetConfirm = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_reset))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val goalSeconds = dailyGoalMinutes * 60L
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StudyTimerDisplay(
                        seconds = timeSeconds,
                        isRunning = isRunning,
                        goalFraction = goalFraction(todayTotalSeconds, goalSeconds),
                        goalLabel = if (dailyGoalMinutes > 0) {
                            stringResource(R.string.study_goal_progress, (todayTotalSeconds / 60L).toInt(), dailyGoalMinutes)
                        } else null
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Streak + subject side by side (a Row, not stacked) so the goal ring above has
                    // room — stacking them overflowed the centred timer box and hid the chips behind
                    // the history card.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (dailyGoalMinutes > 0 && studyStreak > 0) {
                            StudyStreakChip(studyStreak)
                        }
                        SubjectSelectorChip(
                            subject = currentSubject,
                            onClick = { showSubjectPicker = true }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(24.dp)
            ) {
                if (dailyGoalMinutes >= 0) {
                    StudyWeeklyChart(sessions = allSessions, goalMinutes = dailyGoalMinutes)
                    Spacer(modifier = Modifier.height(20.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.study_recent_history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { manualEntry = ManualSessionSeed(LocalDate.now().minusDays(1), "", 0L) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.study_log_past_session),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (pastDays.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.study_no_history), color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pastDays) { day ->
                            DayStudyItem(day, onEditSubject = { subject, seconds ->
                                manualEntry = ManualSessionSeed(day.date, subject, seconds)
                            })
                        }
                    }
                }
            }

            // Bottom Action Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                color = Color.Transparent
            ) {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "scale")

                Button(
                    onClick = { viewModel.toggleTimer() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .scale(scale),
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isRunning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(if (isRunning) R.string.study_pause_session else R.string.study_start_studying),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

/** The pill under the timer showing (and tapping to change) the subject time is attributed to. */
@Composable
fun SubjectSelectorChip(subject: String, onClick: () -> Unit) {
    val chipDescription = stringResource(R.string.cd_choose_subject)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.semantics { contentDescription = chipDescription }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.study_subject_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 2.sp
                )
                Text(
                    text = subject.ifBlank { stringResource(R.string.study_no_subject) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubjectPickerDialog(
    current: String,
    knownSubjects: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var newSubject by remember { mutableStateOf("") }
    // "" (No subject) always offered first, then every previously used subject.
    val options = remember(knownSubjects) { listOf("") + knownSubjects }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_choose_subject)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        FilterChip(
                            selected = option == current,
                            onClick = { onSelect(option) },
                            label = { Text(option.ifBlank { stringResource(R.string.study_no_subject) }) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSubject,
                        onValueChange = { newSubject = it },
                        label = { Text(stringResource(R.string.study_new_subject_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardActions = KeyboardActions(
                            onDone = { if (newSubject.isNotBlank()) onSelect(newSubject) }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { if (newSubject.isNotBlank()) onSelect(newSubject) },
                        enabled = newSubject.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.study_add_subject))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun StudyTimerDisplay(
    seconds: Long,
    isRunning: Boolean,
    goalFraction: Float = 0f,
    goalLabel: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "timer")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center) {
        // Decorative Rings
        Canvas(modifier = Modifier.size(250.dp).rotate(if (isRunning) rotation else 0f)) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        primaryColor.copy(alpha = 0.1f),
                        primaryColor,
                        Color.Transparent
                    )
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Canvas(modifier = Modifier.size(214.dp).rotate(if (isRunning) -rotation * 0.5f else 0f)) {
            drawCircle(
                color = primaryColor.copy(alpha = 0.05f),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Determinate goal-progress arc (Issue #42): a track plus a primary sweep = today's
        // fraction of the daily goal, starting at 12 o'clock. Non-rotating so it reads as progress.
        if (goalLabel != null) {
            val trackColor = primaryColor.copy(alpha = 0.12f)
            Canvas(modifier = Modifier.size(232.dp)) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                if (goalFraction > 0f) {
                    drawArc(color = primaryColor, startAngle = -90f, sweepAngle = 360f * goalFraction, useCenter = false, style = stroke)
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatTime(seconds),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp
                ),
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isRunning) stringResource(R.string.study_focusing) else stringResource(R.string.study_ready),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 4.sp
            )
            if (goalLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = goalLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (goalFraction >= 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Flame + consecutive-day count for the study goal streak (Issue #42). */
@Composable
fun StudyStreakChip(streak: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.study_streak, streak),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Set the daily study goal in minutes; 0 turns the goal/streak UI off (Issue #42). */
@Composable
fun StudyGoalDialog(currentMinutes: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(currentMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_goal_setting)) },
        text = {
            Column {
                Text(stringResource(R.string.study_goal_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.study_goal_minutes_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text.toIntOrNull() ?: 0) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** 7-day bar chart of study minutes with the daily goal as a dashed line (Issue #42). */
@Composable
fun StudyWeeklyChart(sessions: List<StudySession>, goalMinutes: Int) {
    val today = remember { LocalDate.now() }
    val bars = remember(sessions) { weeklyStudyMinutes(sessions, 7, today) }
    val maxMinutes = (bars.maxOfOrNull { it.second } ?: 0).coerceAtLeast(goalMinutes).coerceAtLeast(1)
    val locale = LocalLocale.current.platformLocale

    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val goalColor = MaterialTheme.colorScheme.outline

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barCount = bars.size
                val slot = size.width / barCount
                val barWidth = slot * 0.5f
                bars.forEachIndexed { i, (_, minutes) ->
                    val h = size.height * (minutes.toFloat() / maxMinutes)
                    val left = i * slot + (slot - barWidth) / 2
                    drawRoundRect(
                        color = if (i == barCount - 1) primary else muted,
                        topLeft = androidx.compose.ui.geometry.Offset(left, size.height - h),
                        size = androidx.compose.ui.geometry.Size(barWidth, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                    )
                }
                // Goal line (dashed).
                if (goalMinutes > 0) {
                    val y = size.height * (1f - goalMinutes.toFloat() / maxMinutes)
                    drawLine(
                        color = goalColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEach { (day, _) ->
                Text(
                    text = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** One day's card: the date + grand total, with a per-subject breakdown beneath it. */
@Composable
fun DayStudyItem(day: DayStudy, onEditSubject: (String, Long) -> Unit = { _, _ -> }) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = day.date.format(DateTimeFormatter.ofPattern("MMM dd")),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = day.date.format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = formatDurationCompact(day.totalSeconds * 1000),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Only surface the breakdown when there's something to differentiate — a single
            // uncategorised bucket adds no information over the total already shown above.
            val showBreakdown = day.subjects.size > 1 ||
                (day.subjects.size == 1 && day.subjects.first().subject.isNotBlank())
            if (showBreakdown) {
                Spacer(modifier = Modifier.height(12.dp))
                day.subjects.forEach { subjectTotal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditSubject(subjectTotal.subject, subjectTotal.seconds) }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = subjectTotal.subject.ifBlank { stringResource(R.string.study_no_subject) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDurationCompact(subjectTotal.seconds * 1000),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}

/** Seed values for [ManualSessionDialog] — a blank new entry or an existing row being edited. */
data class ManualSessionSeed(val date: LocalDate, val subject: String, val seconds: Long)

/**
 * Manual entry/edit of a past day's study time (Issue #122). Writes through
 * [StudyViewModel.logManualSession], which keys on (date, subject) exactly like the timer, so
 * saving over an existing row replaces it and 0 clears it. Today isn't offered — the running timer
 * owns today's totals.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualSessionDialog(
    seed: ManualSessionSeed,
    knownSubjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, Long) -> Unit
) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(seed.date) }
    var subject by remember { mutableStateOf(seed.subject) }
    var hours by remember { mutableStateOf((seed.seconds / 3600).toString()) }
    var minutes by remember { mutableStateOf(((seed.seconds % 3600) / 60).toString()) }

    val parsedSeconds = parseManualDurationSeconds(hours, minutes)
    val options = remember(knownSubjects, seed.subject) {
        (listOf("") + knownSubjects + seed.subject).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.study_log_past_session)) },
        text = {
            Column {
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                                // Today and later belong to the timer, so clamp to yesterday.
                                date = if (picked.isBefore(LocalDate.now())) picked else LocalDate.now().minusDays(1)
                            },
                            date.year, date.monthValue - 1, date.dayOfMonth
                        ).also { dialog ->
                            dialog.datePicker.maxDate = System.currentTimeMillis() - 86_400_000L
                            dialog.show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")))
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.study_subject_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        FilterChip(
                            selected = normalizeSubject(option) == normalizeSubject(subject),
                            onClick = { subject = option },
                            label = { Text(option.ifBlank { stringResource(R.string.study_no_subject) }) }
                        )
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text(stringResource(R.string.study_new_subject_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it },
                        label = { Text(stringResource(R.string.study_hours_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = { Text(stringResource(R.string.study_minutes_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { parsedSeconds?.let { onSave(date, subject, it) } },
                enabled = parsedSeconds != null
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
