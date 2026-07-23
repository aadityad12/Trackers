package com.example.apextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BudgetSettingsDialog(
    categories: List<Category>,
    allItems: List<BudgetItem>,
    currentMonth: YearMonth,
    viewModel: BudgetViewModel,
    onDismiss: () -> Unit
) {
    var activeSubScreen by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val securitySettings = remember { SecuritySettings(context) }
    val budgetLocked by securitySettings.budgetLockEnabled.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (activeSubScreen != null) {
                    IconButton(onClick = { activeSubScreen = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
                Text(
                    text = when (activeSubScreen) {
                        "categories" -> stringResource(R.string.budget_manage_categories)
                        "subscriptions" -> stringResource(R.string.budget_manage_subscriptions)
                        else -> stringResource(R.string.budget_settings_title)
                    }
                )
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                when (activeSubScreen) {
                    "categories" -> {
                        CategoriesView(
                            categories = categories,
                            onAdd = { name, color, limit -> viewModel.addCategory(name, color, limit) },
                            onUpdate = { viewModel.updateCategory(it) },
                            onDelete = { viewModel.deleteCategory(it) }
                        )
                    }
                    "subscriptions" -> {
                        SubscriptionsView(viewModel)
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BudgetSettingsItem(stringResource(R.string.budget_manage_categories)) { activeSubScreen = "categories" }
                            BudgetSettingsItem(stringResource(R.string.budget_manage_subscriptions)) { activeSubScreen = "subscriptions" }
                            BudgetSettingsItem(stringResource(R.string.budget_export_csv)) { showExportDialog = true }
                            HorizontalDivider()
                            ModuleLockSetting(
                                checked = budgetLocked,
                                titleRes = R.string.security_lock_budget_title,
                                onCheckedChange = { scope.launch { securitySettings.setBudgetLock(it) } }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done))
            }
        }
    )

    if (showExportDialog) {
        BudgetExportScopeDialog(
            onDismiss = { showExportDialog = false },
            onExport = { scopeToCurrentMonth ->
                val exportItems = if (scopeToCurrentMonth) {
                    allItems.filter { YearMonth.from(it.date) == currentMonth }
                } else {
                    allItems
                }
                val csv = buildBudgetCsv(exportItems, categories)
                val fileName = if (scopeToCurrentMonth) "budget_$currentMonth.csv" else "budget_all_time.csv"
                shareBudgetCsv(context, csv, fileName)
                showExportDialog = false
            }
        )
    }
}

@Composable
fun BudgetExportScopeDialog(onDismiss: () -> Unit, onExport: (scopeToCurrentMonth: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_export_scope_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onExport(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.budget_export_current_month))
                }
                Button(onClick = { onExport(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.budget_export_all_time))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun BudgetSettingsItem(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun CategoriesView(
    categories: List<Category>,
    onAdd: (String, String, Double?) -> Unit,
    onUpdate: (Category) -> Unit,
    onDelete: (Category) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.budget_create_category))
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                CategoryItem(
                    category = category,
                    onEdit = { categoryToEdit = category }
                )
            }
        }
    }

    if (showAddDialog) {
        CategoryDialog(
            title = stringResource(R.string.budget_new_category),
            onDismiss = { showAddDialog = false },
            onConfirm = { name, color, limit -> onAdd(name, color, limit) }
        )
    }

    if (categoryToEdit != null) {
        CategoryDialog(
            title = stringResource(R.string.budget_edit_category),
            initialName = categoryToEdit!!.name,
            initialColor = categoryToEdit!!.colorHex,
            initialLimit = categoryToEdit!!.monthlyLimit,
            onDismiss = { categoryToEdit = null },
            onConfirm = { name, color, limit ->
                onUpdate(categoryToEdit!!.copy(name = name, colorHex = color, monthlyLimit = limit))
                categoryToEdit = null
            },
            onDelete = {
                onDelete(categoryToEdit!!)
                categoryToEdit = null
            }
        )
    }
}

@Composable
fun CategoryItem(category: Category, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(20.dp).background(parseColorSafe(category.colorHex), CircleShape))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(category.name, style = MaterialTheme.typography.bodyMedium)
                    val limit = category.effectiveMonthlyLimit()
                    Text(
                        text = if (limit != null) {
                            stringResource(R.string.budget_limit_summary, formatCurrency(limit, LocalCurrencyCode.current))
                        } else {
                            stringResource(R.string.budget_limit_none)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun CategoryDialog(
    title: String,
    initialName: String = "",
    initialColor: String? = null,
    initialLimit: Double? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    // Rendered bare (not via formatCurrency) because this is an editable numeric field —
    // a "$400.00" string wouldn't survive a round-trip back through the input filter.
    var limit by remember { mutableStateOf(initialLimit?.let { formatLimitForInput(it) } ?: "") }
    val colors = listOf(
        "#ac725e", "#d06b64", "#f83a22", "#fa573c", "#ff7537", "#ffad46",
        "#42d692", "#16a765", "#7bd148", "#b3dc6c", "#fbe983", "#fad165",
        "#92e1c0", "#9fe1e7", "#9fc6e7", "#4986e7", "#9a9cff", "#b99aff",
        "#c2c2c2", "#cabdbf", "#cca6ac", "#f691b2", "#cd74e6", "#a47ae2"
    )
    var selectedColor by remember { mutableStateOf(initialColor ?: colors[15]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_category_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limit,
                    // Same numeric filter as the budget item amount field.
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) limit = it },
                    label = { Text(stringResource(R.string.budget_limit_label)) },
                    supportingText = { Text(stringResource(R.string.budget_limit_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.budget_select_color))
                ColorGrid(colors, selectedColor) { selectedColor = it }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, selectedColor, parseMonthlyLimitInput(limit)); onDismiss() }) {
                Text(stringResource(if (initialName.isEmpty()) R.string.action_create else R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
fun ColorGrid(colors: List<String>, selectedColor: String, onColorSelected: (String) -> Unit) {
    val columns = 6
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.chunked(columns).forEach { rowColors ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowColors.forEach { color ->
                    // Swatches have no text of their own; name the hue so TalkBack can tell them
                    // apart, and mark the selected one (Issue #107).
                    val isSelected = selectedColor == color
                    val colorLabel = stringResource(
                        R.string.cd_color_swatch,
                        stringResource(swatchHueLabelRes(swatchHueOf(color)))
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(parseColorSafe(color), CircleShape)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .selectable(selected = isSelected, onClick = { onColorSelected(color) })
                            .semantics { contentDescription = colorLabel }
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionsView(viewModel: BudgetViewModel) {
    val subscriptions by viewModel.allSubscriptions.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var subToEdit by remember { mutableStateOf<Subscription?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.budget_add_subscription))
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subscriptions) { sub ->
                SubscriptionItem(sub) { subToEdit = sub }
            }
        }
    }

    if (showAddDialog) {
        SubscriptionDialog(
            title = stringResource(R.string.budget_new_subscription),
            onDismiss = { showAddDialog = false },
            onConfirm = { name, amount, date, notes ->
                viewModel.addSubscription(name, amount, date, notes)
            }
        )
    }

    if (subToEdit != null) {
        SubscriptionDialog(
            title = stringResource(R.string.budget_edit_subscription),
            initialName = subToEdit!!.name,
            initialAmount = subToEdit!!.amount.toString(),
            initialDate = subToEdit!!.renewalDate,
            initialNotes = subToEdit!!.notes ?: "",
            onDismiss = { subToEdit = null },
            onConfirm = { name, amount, date, notes ->
                viewModel.updateSubscription(subToEdit!!.copy(name = name, amount = amount, renewalDate = date, notes = notes))
            },
            onDelete = {
                viewModel.deleteSubscription(subToEdit!!)
                subToEdit = null
            }
        )
    }
}

@Composable
fun SubscriptionItem(subscription: Subscription, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(subscription.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.budget_renews_prefix, subscription.renewalDate.format(DateTimeFormatter.ofPattern("MMM dd"))), style = MaterialTheme.typography.bodySmall)
            }
            Text(formatCurrency(subscription.amount, LocalCurrencyCode.current), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SubscriptionDialog(
    title: String,
    initialName: String = "",
    initialAmount: String = "",
    initialDate: LocalDate = LocalDate.now(),
    initialNotes: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, Double, LocalDate, String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var amount by remember { mutableStateOf(initialAmount) }
    var date by remember { mutableStateOf(initialDate) }
    var notes by remember { mutableStateOf(initialNotes) }

    val context = LocalContext.current
    val datePickerDialog = remember {
        android.app.DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it }, label = { Text(stringResource(R.string.label_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.budget_next_renewal, date.format(DateTimeFormatter.ISO_LOCAL_DATE))) }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.label_notes_optional)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, amount.toDoubleOrNull() ?: 0.0, date, notes.ifBlank { null }); onDismiss() }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
