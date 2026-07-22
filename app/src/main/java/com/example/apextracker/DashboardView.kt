package com.example.apextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    onManageGoals: () -> Unit,
    onOpenSettings: () -> Unit,
    signedIn: Boolean,
    isSyncing: Boolean,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    ApexLogo(modifier = Modifier.padding(start = 16.dp).size(22.dp))
                },
                actions = {
                    if (signedIn) {
                        Icon(
                            imageVector = if (isSyncing) Icons.Default.CloudSync else Icons.Default.CloudDone,
                            contentDescription = stringResource(R.string.cd_sync_status),
                            tint = if (isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 4.dp).size(20.dp)
                        )
                    }
                    IconButton(onClick = onManageGoals) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.dashboard_manage_goals))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.menu_settings))
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
            item { TodayCard(state.todayGoals, onToggle = viewModel::toggleTodayGoal, onManageGoals = onManageGoals) }
            item { HeatmapSection(state.weeks, state.today, onDayClick = { selectedDay = it }) }
        }
    }

    selectedDay?.let { day ->
        DayDetailSheet(
            date = day,
            state = state,
            onToggle = { goal -> viewModel.toggleGoalForDate(goal, day) },
            onDismiss = { selectedDay = null }
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
private fun TodayCard(todayGoals: List<GoalStatus>, onToggle: (Goal) -> Unit, onManageGoals: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            if (todayGoals.isEmpty()) {
                Text(
                    stringResource(R.string.dashboard_no_goals),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable { onManageGoals() }
                )
            } else {
                todayGoals.forEach { status -> GoalStatusRow(status, onToggle) }
            }
        }
    }
}

/** One goal's row with an interactive checkbox (MANUAL) or a read-only computed status (AUTO). */
@Composable
private fun GoalStatusRow(status: GoalStatus, onToggle: (Goal) -> Unit) {
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
private fun HeatmapSection(weeks: List<List<DayCell?>>, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
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
                    HeatCell(cell, today, Modifier.weight(1f), onDayClick)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HeatmapLegend()
    }
}

@Composable
private fun HeatCell(cell: DayCell?, today: LocalDate, modifier: Modifier, onDayClick: (LocalDate) -> Unit) {
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
                    .clickable { onDayClick(cell.date) }
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

/** Tap-a-day sheet: that day's goal breakdown, with MANUAL goals editable (backfill). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    date: LocalDate,
    state: DashboardUiState,
    onToggle: (Goal) -> Unit,
    onDismiss: () -> Unit
) {
    val statuses = state.dayGoalStatuses(date)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            if (statuses.isEmpty()) {
                Text(stringResource(R.string.dashboard_day_no_goals), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            } else {
                statuses.forEach { status -> GoalStatusRow(status, onToggle) }
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
