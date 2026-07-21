package com.example.apextracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardView(
    onBackToMenu: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddGoal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onBackToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddGoal = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dashboard_add_goal))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { StreakRow(state.perfectStreak) }
            item { TodayCard(state.todayGoals, onToggle = viewModel::toggleTodayGoal) }
            item { HeatmapSection(state.weeks, state.today) }
            if (state.activeGoals.isNotEmpty()) {
                item { GoalsManageSection(state.activeGoals, onDelete = viewModel::deleteGoal) }
            }
        }
    }

    if (showAddGoal) {
        AddGoalDialog(
            onDismiss = { showAddGoal = false },
            onAdd = { name, type, metric, comparator, threshold, subject ->
                viewModel.addGoal(name, type, metric, comparator, threshold, subject)
                showAddGoal = false
            }
        )
    }
}

@Composable
private fun StreakRow(streak: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.LocalFireDepartment,
            contentDescription = null,
            tint = if (streak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (streak > 0) stringResource(R.string.dashboard_streak, streak)
            else stringResource(R.string.dashboard_streak_none),
            style = MaterialTheme.typography.labelLarge,
            color = if (streak > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun TodayCard(todayGoals: List<GoalStatus>, onToggle: (Goal) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            if (todayGoals.isEmpty()) {
                Text(stringResource(R.string.dashboard_no_goals), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            } else {
                todayGoals.forEach { status ->
                    TodayGoalRow(status, onToggle)
                }
            }
        }
    }
}

@Composable
private fun TodayGoalRow(status: GoalStatus, onToggle: (Goal) -> Unit) {
    val goal = status.goal
    val isManual = goal.type == GoalType.MANUAL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isManual) Modifier.clickable { onToggle(goal) } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isManual) {
            Checkbox(
                checked = status.satisfied,
                onCheckedChange = { onToggle(goal) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
        } else {
            Icon(
                if (status.satisfied) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (status.satisfied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp).size(24.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(goal.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (!isManual) {
                Text(goalRuleText(goal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (!isManual) {
            Text(
                text = if (status.satisfied) stringResource(R.string.dashboard_auto_met) else stringResource(R.string.dashboard_auto_unmet),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (status.satisfied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    }
}

private val WEEKDAY_LETTERS = listOf("S", "M", "T", "W", "T", "F", "S")
private val GUTTER_WIDTH = 30.dp

@Composable
private fun HeatmapSection(weeks: List<List<DayCell?>>, today: LocalDate) {
    Column(Modifier.fillMaxWidth()) {
        // Weekday header — gutter spacer then seven day letters.
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(Modifier.width(GUTTER_WIDTH))
            WEEKDAY_LETTERS.forEach { letter ->
                Text(
                    letter,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        weeks.forEach { week ->
            val monthLabel = week.firstOrNull { it?.date?.dayOfMonth == 1 }?.date
                ?.format(DateTimeFormatter.ofPattern("MMM"))?.uppercase() ?: ""
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(GUTTER_WIDTH)) {
                    if (monthLabel.isNotEmpty()) {
                        Text(monthLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                    }
                }
                week.forEach { cell ->
                    HeatCell(cell, today, Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HeatmapLegend()
    }
}

@Composable
private fun HeatCell(cell: DayCell?, today: LocalDate, modifier: Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
    ) {
        if (cell != null) {
            val isToday = cell.date == today
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(cellColor(cell.bucket))
                    .then(
                        if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        else Modifier
                    )
            )
        }
    }
}

@Composable
private fun HeatmapLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dashboard_legend_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(6.dp))
        (0..4).forEach { bucket ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(cellColor(bucket))
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.dashboard_legend_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun GoalsManageSection(goals: List<Goal>, onDelete: (Goal) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dashboard_manage_goals), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        goals.forEach { goal ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    val rule = goalRuleText(goal)
                    if (rule.isNotEmpty()) {
                        Text(rule, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                IconButton(onClick = { onDelete(goal) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dashboard_delete_goal), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** Theme-accent intensity ramp: empty/no-goals is neutral, then primary deepens toward a perfect day. */
@Composable
private fun cellColor(bucket: Int): Color {
    val cs = MaterialTheme.colorScheme
    return when (bucket) {
        -1 -> cs.surfaceVariant.copy(alpha = 0.30f) // no active goals that day
        0 -> cs.onSurface.copy(alpha = 0.10f)       // tracked, none completed
        1 -> cs.primary.copy(alpha = 0.35f)
        2 -> cs.primary.copy(alpha = 0.55f)
        3 -> cs.primary.copy(alpha = 0.78f)
        else -> cs.primary                           // 4 = perfect day
    }
}

/** Human-readable rule for an AUTO goal (empty for MANUAL). */
@Composable
private fun goalRuleText(goal: Goal): String {
    if (goal.type != GoalType.AUTO) return ""
    val t = goal.threshold ?: return ""
    val tStr = if (t % 1.0 == 0.0) t.toLong().toString() else t.toString()
    val subjectSuffix = goal.subject?.let { stringResource(R.string.goal_rule_subject_suffix, it) } ?: ""
    val under = goal.comparator == GoalComparator.UNDER
    return when (goal.metric) {
        GoalMetric.SCREEN_TIME ->
            if (under) stringResource(R.string.goal_rule_screen_under, tStr) else stringResource(R.string.goal_rule_screen_over, tStr)
        GoalMetric.STUDY ->
            if (under) stringResource(R.string.goal_rule_study_under, tStr, subjectSuffix) else stringResource(R.string.goal_rule_study_over, tStr, subjectSuffix)
        GoalMetric.SPEND -> {
            val amount = formatCurrency(t, LocalCurrencyCode.current)
            if (under) stringResource(R.string.goal_rule_spend_under, amount) else stringResource(R.string.goal_rule_spend_over, amount)
        }
        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, type: String, metric: String?, comparator: String?, threshold: Double?, subject: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(GoalType.MANUAL) }
    var metric by remember { mutableStateOf(GoalMetric.SCREEN_TIME) }
    var comparator by remember { mutableStateOf(GoalComparator.UNDER) }
    var thresholdText by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }

    val threshold = thresholdText.trim().toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }
    val isAuto = type == GoalType.AUTO
    val canSave = name.isNotBlank() && (!isAuto || threshold != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_new_title)) },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (isAuto) {
                        onAdd(name, GoalType.AUTO, metric, comparator, threshold, if (metric == GoalMetric.STUDY) subject else null)
                    } else {
                        onAdd(name, GoalType.MANUAL, null, null, null, null)
                    }
                }
            ) { Text(stringResource(R.string.goal_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.goal_cancel)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.goal_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !isAuto, onClick = { type = GoalType.MANUAL }, label = { Text(stringResource(R.string.goal_type_manual)) })
                    FilterChip(selected = isAuto, onClick = { type = GoalType.AUTO }, label = { Text(stringResource(R.string.goal_type_auto)) })
                }
                if (isAuto) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = metric == GoalMetric.SCREEN_TIME, onClick = { metric = GoalMetric.SCREEN_TIME }, label = { Text(stringResource(R.string.goal_metric_screen)) })
                        FilterChip(selected = metric == GoalMetric.STUDY, onClick = { metric = GoalMetric.STUDY }, label = { Text(stringResource(R.string.goal_metric_study)) })
                        FilterChip(selected = metric == GoalMetric.SPEND, onClick = { metric = GoalMetric.SPEND }, label = { Text(stringResource(R.string.goal_metric_spend)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = comparator == GoalComparator.UNDER, onClick = { comparator = GoalComparator.UNDER }, label = { Text(stringResource(R.string.goal_dir_under)) })
                        FilterChip(selected = comparator == GoalComparator.OVER, onClick = { comparator = GoalComparator.OVER }, label = { Text(stringResource(R.string.goal_dir_over)) })
                    }
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        label = { Text(stringResource(if (metric == GoalMetric.SPEND) R.string.goal_threshold_amount else R.string.goal_threshold_hours)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (metric == GoalMetric.STUDY) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text(stringResource(R.string.goal_subject_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    )
}
