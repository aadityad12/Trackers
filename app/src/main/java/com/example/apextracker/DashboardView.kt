package com.example.apextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

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
    // null = rolling last 12 months (the default window), otherwise a specific calendar year.
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }

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
        // A Column (not a LazyColumn): the heatmap takes whatever height is left and sizes its
        // cells to fit, so a full year is on screen with no page scrolling (Issue #128).
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StreakRow(state.perfectStreak)
            // The checklist is capped and scrolls internally so a long goal list can never push
            // the graph off screen.
            Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                TodayCard(state.todayGoals, state.loaded, onToggle = viewModel::toggleTodayGoal, onManageGoals = onManageGoals)
            }
            HeatmapSection(
                state = state,
                selectedYear = selectedYear,
                onSelectYear = { selectedYear = it },
                onDayClick = { selectedDay = it },
                modifier = Modifier.weight(1f)
            )
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
private fun TodayCard(todayGoals: List<GoalStatus>, loaded: Boolean, onToggle: (Goal) -> Unit, onManageGoals: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.dashboard_today), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            if (todayGoals.isEmpty()) {
                // Only claim "no goals" once the Room flows have actually emitted — the seeded
                // EMPTY state would otherwise flash the empty message on launch for a user who
                // does have goals (Issue #118), same gate GoalsView already uses.
                if (loaded) {
                    Text(
                        stringResource(R.string.dashboard_no_goals),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.clickable { onManageGoals() }
                    )
                }
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

private val GUTTER_WIDTH = 30.dp
private val HEADER_ROW_HEIGHT = 16.dp
private val MAX_CELL = 20.dp
private val MIN_CELL = 7.dp
private val GRID_SPACING = 8.dp
private val MONTH_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
private val HEATCELL_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

@Composable
private fun HeatmapSection(
    state: DashboardUiState,
    selectedYear: Int?,
    onSelectYear: (Int?) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = state.today
    val (rangeStart, rangeEnd) = remember(selectedYear, today) { heatmapRange(selectedYear, today) }
    val weeks = remember(state, rangeStart, rangeEnd) {
        heatmapWeeks(rangeStart, rangeEnd) { date -> state.dayCell(date) }
    }
    val years = remember(state.earliestGoalStart, today) { heatmapYears(state.earliestGoalStart, today) }

    // Sunday-first to match the rows heatmapWeeks builds, but the letters themselves come from the
    // locale rather than a hardcoded English list (Issue #120), same pattern as BudgetCalendar.
    val locale = LocalLocale.current.platformLocale
    val weekdayLetters = remember(locale) {
        (0L..6L).map { DayOfWeek.SUNDAY.plus(it).getDisplayName(TextStyle.NARROW, locale) }
    }

    Column(modifier.fillMaxWidth()) {
        YearSelector(years = years, selectedYear = selectedYear, onSelectYear = onSelectYear)
        Spacer(Modifier.height(8.dp))

        // Cells shrink to whatever fits the remaining height, capped so a short window (a new
        // user's first weeks) doesn't blow them up into giant squares.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            val rows = weeks.size.coerceAtLeast(1)
            // Height left for the cells once the weekday header, the legend and their spacing are
            // accounted for. Floored at MIN_CELL so cells stay visible/tappable on a short screen —
            // the grid scrolls in that case rather than being clipped.
            val byHeight = (maxHeight - HEADER_ROW_HEIGHT - GRID_SPACING) / rows
            val byWidth = (maxWidth - GUTTER_WIDTH) / 7
            val cellSize = minOf(byWidth, byHeight, MAX_CELL).coerceAtLeast(MIN_CELL)
            val gridWidth = GUTTER_WIDTH + cellSize * 7

            Column(Modifier.width(gridWidth).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().height(HEADER_ROW_HEIGHT)) {
                    Spacer(Modifier.width(GUTTER_WIDTH))
                    weekdayLetters.forEach { letter ->
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
                        ?.format(MONTH_LABEL_FORMAT)?.uppercase() ?: ""
                    // Fixed row height: the month label must not inflate the twelve rows it
                    // lands on, or a full year stops fitting on screen.
                    Row(Modifier.fillMaxWidth().height(cellSize), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(GUTTER_WIDTH), contentAlignment = Alignment.CenterEnd) {
                            if (monthLabel.isNotEmpty()) {
                                Text(
                                    monthLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    fontSize = 8.sp,
                                    lineHeight = 8.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                        week.forEach { cell ->
                            HeatCell(cell, today, Modifier.size(cellSize), onDayClick)
                        }
                    }
                }

            }
        }

        // Outside the grid column: it is only ~7 cells wide, which would wrap the legend's text.
        Spacer(Modifier.height(4.dp))
        HeatmapLegend()
    }
}

/** "Last 12 months" plus one chip per calendar year with history (Issue #128). */
@Composable
private fun YearSelector(years: List<Int>, selectedYear: Int?, onSelectYear: (Int?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedYear == null,
            onClick = { onSelectYear(null) },
            label = { Text(stringResource(R.string.dashboard_window_rolling)) }
        )
        years.forEach { year ->
            FilterChip(
                selected = selectedYear == year,
                onClick = { onSelectYear(year) },
                label = { Text(year.toString()) }
            )
        }
    }
}

@Composable
private fun HeatCell(cell: DayCell?, today: LocalDate, modifier: Modifier, onDayClick: (LocalDate) -> Unit) {
    Box(
        modifier = modifier.padding(1.dp)
    ) {
        if (cell != null) {
            val isToday = cell.date == today
            // The cell has no text content of its own, so TalkBack needs the date and completion
            // state spelled out — tapping a cell is the only way into the day sheet (Issue #106).
            val dateText = cell.date.format(HEATCELL_DATE_FORMAT)
            val label = stringResource(
                R.string.cd_dashboard_day,
                if (isToday) stringResource(R.string.cd_dashboard_day_today, dateText) else dateText,
                cell.fraction?.let {
                    stringResource(R.string.cd_dashboard_day_percent, (it * 100).roundToInt())
                } ?: stringResource(R.string.cd_dashboard_day_untracked)
            )
            val actionLabel = stringResource(R.string.cd_dashboard_day_action)
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(cellColor(cell.bucket))
                    .then(
                        if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .clickable(onClickLabel = actionLabel) { onDayClick(cell.date) }
                    .semantics { contentDescription = label }
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
