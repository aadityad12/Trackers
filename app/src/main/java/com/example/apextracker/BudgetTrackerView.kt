package com.example.apextracker

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetTrackerApp(onBackToMenu: () -> Unit, viewModel: BudgetViewModel = viewModel()) {
    val items by viewModel.allItems.collectAsState(initial = emptyList())
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
    val subscriptions by viewModel.allSubscriptions.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<BudgetItem?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val overallLimit by viewModel.overallMonthlyLimit.collectAsState(initial = null)
    var isSearching by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    // Selected month is shared between the list and calendar views so toggling
    // doesn't jump the user to a different month.
    var selectedMonth by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.now()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(stringResource(R.string.budget_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(stringResource(R.string.budget_title), 
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            viewModel.setSearchQuery("")
                        } else onBackToMenu()
                    }) {
                        Icon(
                            if (isSearching) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Home,
                            contentDescription = if (isSearching) stringResource(R.string.cd_back) else stringResource(R.string.cd_home)
                        )
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                        }
                    }
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(
                            if (showCalendar) Icons.Default.ViewAgenda else Icons.Default.CalendarMonth,
                            contentDescription = if (showCalendar) stringResource(R.string.cd_show_list) else stringResource(R.string.cd_show_calendar),
                            tint = if (showCalendar) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.menu_settings))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_item))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
        ) {
            if (showCalendar) {
                // Like the list view, the calendar only shows BudgetItems — pending
                // future subscription renewals aren't items yet, so they don't appear.
                BudgetCalendarView(
                    items = items,
                    categories = categories,
                    currentMonth = selectedMonth,
                    onMonthChange = { selectedMonth = it }
                )
            } else {
                BudgetOverview(
                    items, categories, subscriptions,
                    selectedMonth = selectedMonth,
                    onMonthChange = { selectedMonth = it },
                    onEdit = { itemToEdit = it },
                    searchQuery = searchQuery,
                    overallLimit = overallLimit
                )
            }
        }

        if (showAddDialog) {
            BudgetItemDialog(
                title = stringResource(R.string.budget_add_item_title),
                categories = categories,
                onDismiss = { showAddDialog = false },
                onConfirm = { title, amount, description, date, categoryId ->
                    viewModel.addItem(title, amount, description, date, categoryId)
                    showAddDialog = false
                }
            )
        }

        if (itemToEdit != null) {
            BudgetItemDialog(
                title = stringResource(R.string.budget_edit_item_title),
                initialTitle = budgetItemBaseTitle(itemToEdit!!.title),
                initialAmount = itemToEdit!!.amount.toString(),
                initialDescription = itemToEdit!!.description ?: "",
                initialDate = itemToEdit!!.date,
                initialCategoryId = itemToEdit!!.categoryId,
                categories = categories,
                onDismiss = { itemToEdit = null },
                onConfirm = { title, amount, description, date, categoryId ->
                    viewModel.updateItem(itemToEdit!!.copy(
                        title = title,
                        amount = amount,
                        description = description,
                        date = date,
                        categoryId = categoryId
                    ))
                    itemToEdit = null
                },
                onDelete = {
                    val deleted = itemToEdit!!
                    viewModel.deleteItem(deleted)
                    itemToEdit = null
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.deleted_quoted, deleted.title),
                            actionLabel = resources.getString(R.string.action_undo),
                            duration = SnackbarDuration.Short
                        )
                        // The cloud delete has already been pushed; undoing re-pushes
                        // the same cloudId, which recreates the doc.
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restoreItem(deleted)
                        }
                    }
                }
            )
        }

        if (showSettingsDialog) {
            BudgetSettingsDialog(
                categories = categories,
                allItems = items,
                currentMonth = selectedMonth,
                viewModel = viewModel,
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

// YearMonth isn't Parcelable, so rememberSaveable needs an explicit saver.
private val YearMonthSaver = Saver<YearMonth, String>(
    save = { it.toString() },
    restore = { YearMonth.parse(it) }
)

@Composable
fun BudgetOverview(
    items: List<BudgetItem>,
    categories: List<Category>,
    subscriptions: List<Subscription>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onEdit: (BudgetItem) -> Unit,
    searchQuery: String = "",
    overallLimit: Double? = null
) {
    val availableMonths = items.map { YearMonth.from(it.date) }.distinct().sortedDescending()
    val monthToDisplay = if (availableMonths.contains(selectedMonth)) selectedMonth 
                         else availableMonths.firstOrNull() ?: selectedMonth

    val monthItems = items.filter { YearMonth.from(it.date) == monthToDisplay }
    // Only the transactions list narrows to the query — the totals, pie, limits and trend chart
    // keep describing the whole month, which is what those summaries are for (Issue #123).
    val categoryNames = categories.associate { it.id to it.name }
    val visibleItems = filterBudgetItems(monthItems, categoryNames, searchQuery)
    
    val pendingSubs = if (monthToDisplay == YearMonth.now()) {
        subscriptions.filter { sub ->
            YearMonth.from(sub.renewalDate) == monthToDisplay && sub.renewalDate.isAfter(LocalDate.now()) &&
                matchesQuery(searchQuery, sub.name, sub.notes)
        }
    } else emptyList()

    val totalExpenditure = monthItems.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthSelectorCompact(
            currentMonth = monthToDisplay,
            onMonthChange = onMonthChange
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCardModern(totalExpenditure, monthItems, categories, pendingSubs)
            }

            // Sits above trends: a cap the user set is more actionable than history.
            // Gated here as well as inside the card so the spacer doesn't leave a gap
            // for users who have never set a limit.
            if (categories.any { it.effectiveMonthlyLimit() != null } || overallLimit != null) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    BudgetLimitsCard(
                        items = items,
                        categories = categories,
                        month = monthToDisplay,
                        overallLimit = overallLimit
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                BudgetTrendsCard(items = items, selectedMonth = monthToDisplay, onMonthSelected = onMonthChange)
            }

            if (visibleItems.isNotEmpty() || pendingSubs.isNotEmpty()) {
                val sortedItems = visibleItems.sortedByDescending { it.date }
                
                item { 
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.budget_transactions), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                
                items(pendingSubs.sortedBy { it.renewalDate }) { sub ->
                    val category = subscriptionsCategory()
                    BudgetListItem(
                        BudgetItem(title = sub.name, amount = sub.amount, date = sub.renewalDate, categoryId = -1L), 
                        category, 
                        onClick = {},
                        isPending = true
                    )
                }

                items(sortedItems) { item ->
                    val category = if (item.categoryId == -1L) {
                        subscriptionsCategory()
                    } else {
                        categories.find { it.id == item.categoryId }
                    }
                    BudgetListItem(item, category, onClick = { onEdit(item) })
                }
            } else {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (searchQuery.isNotBlank() && monthItems.isNotEmpty()) {
                                stringResource(R.string.budget_search_no_results, searchQuery)
                            } else {
                                stringResource(R.string.budget_no_data)
                            },
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MonthSelectorCompact(currentMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_prev))
            }
            
            Text(
                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next))
            }
        }
    }
}

@Composable
fun SummaryCardModern(
    total: Double, 
    items: List<BudgetItem>, 
    categories: List<Category>,
    pendingSubs: List<Subscription>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.budget_total_monthly_spend),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatCurrency(total, LocalCurrencyCode.current),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            
            if (items.isNotEmpty() || pendingSubs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                ExpensePieChartModern(items, categories, pendingSubs)
            }
        }
    }
}

@Composable
fun ExpensePieChartModern(
    items: List<BudgetItem>, 
    categories: List<Category>,
    pendingSubs: List<Subscription>
) {
    val itemsByCategory = items.groupBy { it.categoryId }
    val totalExpenses = items.sumOf { it.amount }
    val totalPending = pendingSubs.sumOf { it.amount }
    val totalCombined = totalExpenses + totalPending
    
    if (totalCombined == 0.0) return

    val chartData = mutableListOf<Triple<String, Float, Color>>()
    // Hoisted out of the loop: it reads MaterialTheme, and the value is identical for every -1L item.
    val subsCategory = subscriptionsCategory()
    // Legend labels are user-facing text, so they go through strings.xml like the rest of the
    // screen (Issue #114) rather than being baked in as English literals.
    val uncategorizedLabel = stringResource(R.string.budget_uncategorized)
    val pendingLabel = stringResource(R.string.budget_pending_legend)

    itemsByCategory.forEach { (catId, catItems) ->
        val category = if (catId == -1L) {
            subsCategory
        } else {
            categories.find { it.id == catId }
        }
        val color = category?.let { parseColorSafe(it.colorHex) } ?: Color.Gray
        val amount = catItems.sumOf { it.amount }
        chartData.add(Triple(category?.name ?: uncategorizedLabel, amount.toFloat(), color))
    }

    if (totalPending > 0) {
        chartData.add(Triple(pendingLabel, totalPending.toFloat(), MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)))
    }

    val sortedData = chartData.sortedByDescending { it.second }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                sortedData.forEach { (_, amount, color) ->
                    val sweepAngle = (amount / totalCombined.toFloat()) * 360f
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Butt)
                    )
                    startAngle += sweepAngle
                }
            }
            Text(
                text = "${(totalExpenses/totalCombined * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
        
        Spacer(modifier = Modifier.width(24.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sortedData.take(4).forEach { (name, amount, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$name",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${String.format(Locale.US, "%.0f%%", (amount / totalCombined.toFloat()) * 100)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (sortedData.size > 4) {
                Text(stringResource(R.string.budget_plus_n_more, sortedData.size - 4), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
            }
        }
    }
}
