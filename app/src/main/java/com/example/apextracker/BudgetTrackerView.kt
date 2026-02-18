package com.example.apextracker

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetTrackerApp(onBackToMenu: () -> Unit, viewModel: BudgetViewModel = viewModel()) {
    val items by viewModel.allItems.collectAsState(initial = emptyList())
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
    val subscriptions by viewModel.allSubscriptions.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<BudgetItem?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text("BUDGET FLOW", 
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToMenu) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
        ) {
            BudgetOverview(items, categories, subscriptions, onEdit = { itemToEdit = it })
        }

        if (showAddDialog) {
            BudgetItemDialog(
                title = "Add Budget Item",
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
                title = "Edit Budget Item",
                initialTitle = itemToEdit!!.title,
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
                    viewModel.deleteItem(itemToEdit!!)
                    itemToEdit = null
                }
            )
        }

        if (showSettingsDialog) {
            BudgetSettingsDialog(
                categories = categories,
                viewModel = viewModel,
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
fun BudgetOverview(
    items: List<BudgetItem>, 
    categories: List<Category>, 
    subscriptions: List<Subscription>,
    onEdit: (BudgetItem) -> Unit
) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    
    val availableMonths = items.map { YearMonth.from(it.date) }.distinct().sortedDescending()
    val monthToDisplay = if (availableMonths.contains(selectedMonth)) selectedMonth 
                         else availableMonths.firstOrNull() ?: selectedMonth

    val monthItems = items.filter { YearMonth.from(it.date) == monthToDisplay }
    
    val pendingSubs = if (monthToDisplay == YearMonth.now()) {
        subscriptions.filter { sub ->
            YearMonth.from(sub.renewalDate) == monthToDisplay && sub.renewalDate.isAfter(LocalDate.now())
        }
    } else emptyList()

    val totalExpenditure = monthItems.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthSelectorCompact(
            currentMonth = monthToDisplay,
            onMonthChange = { selectedMonth = it }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SummaryCardModern(totalExpenditure, monthItems, categories, pendingSubs)
            }

            if (monthItems.isNotEmpty() || pendingSubs.isNotEmpty()) {
                val sortedItems = monthItems.sortedByDescending { it.date }
                
                item { 
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("TRANSACTIONS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                
                items(pendingSubs.sortedBy { it.renewalDate }) { sub ->
                    val category = Category(id = -1L, name = "Subscription", colorHex = "#FFD700")
                    BudgetListItem(
                        BudgetItem(title = sub.name, amount = sub.amount, date = sub.renewalDate, categoryId = -1L), 
                        category, 
                        onClick = {},
                        isPending = true
                    )
                }

                items(sortedItems) { item ->
                    val category = if (item.categoryId == -1L) {
                        Category(id = -1L, name = "Subscriptions", colorHex = "#FFD700")
                    } else {
                        categories.find { it.id == item.categoryId }
                    }
                    BudgetListItem(item, category, onClick = { onEdit(item) })
                }
            } else {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No data for this period", color = MaterialTheme.colorScheme.outline)
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
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev")
            }
            
            Text(
                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
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
                        text = "Total Monthly Spend",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", total)}",
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
    
    itemsByCategory.forEach { (catId, catItems) ->
        val category = if (catId == -1L) {
            Category(id = -1L, name = "Subscriptions", colorHex = "#FFD700")
        } else {
            categories.find { it.id == catId }
        }
        val color = category?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: Color.Gray
        val amount = catItems.sumOf { it.amount }
        chartData.add(Triple(category?.name ?: "Uncategorized", amount.toFloat(), color))
    }

    if (totalPending > 0) {
        chartData.add(Triple("Pending", totalPending.toFloat(), MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)))
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
                Text("+ ${sortedData.size - 4} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
            }
        }
    }
}
