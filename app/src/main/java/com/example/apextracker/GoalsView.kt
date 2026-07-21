package com.example.apextracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsView(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    // editorState: null = closed; holds the goal being edited, or a "new" marker.
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }

    val active = state.allGoals.filter { it.archivedDate == null }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val archived = state.allGoals.filter { it.archivedDate != null }.sortedByDescending { it.archivedDate }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.titleSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { editorTarget = EditorTarget.New }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dashboard_add_goal))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (state.loaded && active.isEmpty() && archived.isEmpty()) {
            Box(Modifier.padding(innerPadding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.dashboard_no_goals), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (active.isNotEmpty()) {
                    item { GoalsSectionHeader(stringResource(R.string.goals_active)) }
                    items(active, key = { it.id }) { goal ->
                        GoalRow(
                            goal = goal,
                            onEdit = { editorTarget = EditorTarget.Edit(goal) },
                            onArchive = { viewModel.archiveGoal(goal) }
                        )
                    }
                }
                if (archived.isNotEmpty()) {
                    item { GoalsSectionHeader(stringResource(R.string.goals_archived)) }
                    items(archived, key = { it.id }) { goal ->
                        ArchivedGoalRow(
                            goal = goal,
                            onUnarchive = { viewModel.unarchiveGoal(goal) },
                            onDelete = { viewModel.deleteGoal(goal) }
                        )
                    }
                }
            }
        }
    }

    editorTarget?.let { target ->
        val existing = (target as? EditorTarget.Edit)?.goal
        GoalEditDialog(
            existing = existing,
            onDismiss = { editorTarget = null },
            onSave = { name, type, metric, comparator, threshold, subject ->
                if (existing == null) {
                    viewModel.addGoal(name, type, metric, comparator, threshold, subject)
                } else {
                    viewModel.updateGoal(existing, name, type, metric, comparator, threshold, subject)
                }
                editorTarget = null
            }
        )
    }
}

private sealed interface EditorTarget {
    data object New : EditorTarget
    data class Edit(val goal: Goal) : EditorTarget
}

@Composable
private fun GoalsSectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun GoalRow(goal: Goal, onEdit: () -> Unit, onArchive: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GoalLabel(goal, Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.goal_edit), tint = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onArchive) {
            Icon(Icons.Default.Archive, contentDescription = stringResource(R.string.goal_archive), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ArchivedGoalRow(goal: Goal, onUnarchive: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(goal.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            val rule = goalRuleText(goal)
            if (rule.isNotEmpty()) {
                Text(rule, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
            }
        }
        IconButton(onClick = onUnarchive) {
            Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.goal_unarchive), tint = MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dashboard_delete_goal), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun GoalLabel(goal: Goal, modifier: Modifier) {
    Column(modifier) {
        Text(goal.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        val rule = goalRuleText(goal)
        Text(
            text = rule.ifEmpty { stringResource(R.string.goal_type_manual) },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** Human-readable rule for an AUTO goal (empty for MANUAL). Shared by the dashboard and this screen. */
@Composable
internal fun goalRuleText(goal: Goal): String {
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

/** Shared add/edit dialog. [existing] null = create; otherwise fields prefill from that goal. */
@Composable
internal fun GoalEditDialog(
    existing: Goal?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, metric: String?, comparator: String?, threshold: Double?, subject: String?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: GoalType.MANUAL) }
    var metric by remember { mutableStateOf(existing?.metric ?: GoalMetric.SCREEN_TIME) }
    var comparator by remember { mutableStateOf(existing?.comparator ?: GoalComparator.UNDER) }
    var thresholdText by remember {
        mutableStateOf(
            existing?.threshold?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
        )
    }
    var subject by remember { mutableStateOf(existing?.subject ?: "") }

    val threshold = thresholdText.trim().toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }
    val isAuto = type == GoalType.AUTO
    val canSave = name.isNotBlank() && (!isAuto || threshold != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.goal_new_title else R.string.goal_edit_title)) },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    if (isAuto) {
                        onSave(name, GoalType.AUTO, metric, comparator, threshold, if (metric == GoalMetric.STUDY) subject else null)
                    } else {
                        onSave(name, GoalType.MANUAL, null, null, null, null)
                    }
                }
            ) { Text(stringResource(if (existing == null) R.string.goal_save else R.string.goal_update)) }
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
