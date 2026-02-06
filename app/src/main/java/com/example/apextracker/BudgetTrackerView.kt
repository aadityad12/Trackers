package com.example.apextracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2

enum class BudgetScreen {
    Overview, Calendar, Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetTrackerApp(onBackToMenu: () -> Unit, viewModel: BudgetViewModel = viewModel()) {
    val items by viewModel.allItems.collectAsState(initial = emptyList())
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
    val subscriptions by viewModel.allSubscriptions.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<BudgetItem?>(null) }
    var currentScreen by remember { mutableStateOf(BudgetScreen.Overview) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(when(currentScreen) {
                        BudgetScreen.Overview -> "Overview"
                        BudgetScreen.Calendar -> "Calendar View"
                        BudgetScreen.Settings -> "Settings"
                    }) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentScreen == BudgetScreen.Overview) {
                            onBackToMenu()
                        } else {
                            currentScreen = BudgetScreen.Overview
                        }
                    }) {
                        Icon(
                            imageVector = if (currentScreen == BudgetScreen.Overview) Icons.Default.Home else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (currentScreen == BudgetScreen.Overview) "Home" else "Back to Overview"
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == BudgetScreen.Overview,
                    onClick = { currentScreen = BudgetScreen.Overview },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Overview") },
                    label = { Text("Overview") }
                )
                NavigationBarItem(
                    selected = currentScreen == BudgetScreen.Calendar,
                    onClick = { currentScreen = BudgetScreen.Calendar },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                    label = { Text("Calendar") }
                )
                NavigationBarItem(
                    selected = currentScreen == BudgetScreen.Settings,
                    onClick = { currentScreen = BudgetScreen.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        },
        floatingActionButton = {
            if (currentScreen != BudgetScreen.Settings) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentScreen) {
                BudgetScreen.Overview -> OverviewView(items, categories, subscriptions, onEdit = { itemToEdit = it })
                BudgetScreen.Calendar -> BudgetCalendarView(items, categories)
                BudgetScreen.Settings -> BudgetSettingsView(categories, viewModel)
            }
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
    }
}

@Composable
fun OverviewView(
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
        MonthSelector(
            currentMonth = monthToDisplay,
            onMonthChange = { selectedMonth = it }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = CardDefaults.shape
                ) {
                    ExpenditureCard(totalExpenditure, monthItems, categories, pendingSubs)
                }
            }

            if (monthItems.isNotEmpty() || pendingSubs.isNotEmpty()) {
                val sortedItems = monthItems.sortedByDescending { it.date }
                
                item { Spacer(modifier = Modifier.height(16.dp)) }
                
                items(pendingSubs.sortedBy { it.renewalDate }) { sub ->
                    val category = Category(id = -1L, name = "Pending Subscription", colorHex = "#FFD700")
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        BudgetListItem(
                            BudgetItem(title = sub.name, amount = sub.amount, date = sub.renewalDate, categoryId = -1L), 
                            category, 
                            onClick = {},
                            isPending = true
                        )
                    }
                }

                items(sortedItems) { item ->
                    val category = if (item.categoryId == -1L) {
                        Category(id = -1L, name = "Subscriptions", colorHex = "#FFD700")
                    } else {
                        categories.find { it.id == item.categoryId }
                    }
                    
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        BudgetListItem(item, category, onClick = { onEdit(item) })
                    }
                }
            }
        }
    }
}

@Composable
fun MonthSelector(currentMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
        }
        Text(
            text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleLarge
        )
        IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
        }
    }
}

@Composable
fun ExpenditureCard(
    totalExpenditure: Double, 
    monthItems: List<BudgetItem>, 
    categories: List<Category>,
    pendingSubs: List<Subscription>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Total Expenditure This Month:", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "$${String.format(Locale.US, "%.2f", totalExpenditure)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            if (monthItems.isNotEmpty() || pendingSubs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                ExpensePieChart(monthItems, categories, pendingSubs)
            } else {
                Text(
                    text = "No expenses recorded for this month.",
                    modifier = Modifier.padding(vertical = 32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun ExpensePieChart(
    items: List<BudgetItem>, 
    categories: List<Category>,
    pendingSubs: List<Subscription>
) {
    val itemsByCategory = items.groupBy { it.categoryId }
    val totalExpenses = items.sumOf { it.amount }
    val totalPending = pendingSubs.sumOf { it.amount }
    val totalCombined = totalExpenses + totalPending
    
    if (totalCombined == 0.0) return

    val haptic = LocalHapticFeedback.current
    var showPendingBreakdown by remember { mutableStateOf(false) }

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
        chartData.add(Triple("Pending Subscriptions", totalPending.toFloat(), Color(0xFFFFD700).copy(alpha = 0.4f)))
    }

    val sortedData = chartData.sortedByDescending { it.second }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp).pointerInput(Unit) {
            detectTapGestures { offset ->
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val angle = Math.toDegrees(atan2((offset.y - centerY).toDouble(), (offset.x - centerX).toDouble())).toFloat()
                val normalizedAngle = (angle + 90f + 360f) % 360f
                
                var currentAngle = 0f
                sortedData.forEach { (name, amount, _) ->
                    val sweepAngle = (amount / totalCombined.toFloat()) * 360f
                    if (normalizedAngle >= currentAngle && normalizedAngle <= currentAngle + sweepAngle) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (name == "Pending Subscriptions") {
                            showPendingBreakdown = true
                        }
                    }
                    currentAngle += sweepAngle
                }
            }
        }) {
            var startAngle = -90f
            sortedData.forEach { (_, amount, color) ->
                val sweepAngle = (amount / totalCombined.toFloat()) * 360f
                drawArc(color = color, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true)
                drawArc(color = Color.White, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true, style = Stroke(width = 2.dp.toPx()))
                startAngle += sweepAngle
            }
            drawCircle(color = Color.White, radius = size.minDimension / 4)
        }
        
        Spacer(modifier = Modifier.width(24.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sortedData.take(6).forEach { (name, amount, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = name == "Pending Subscriptions") {
                    if (name == "Pending Subscriptions") showPendingBreakdown = true
                }) {
                    Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$name (${String.format(Locale.US, "%.0f%%", (amount / totalCombined.toFloat()) * 100)})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (name == "Pending Subscriptions") FontWeight.Bold else FontWeight.Normal,
                        textDecoration = if (name == "Pending Subscriptions") TextDecoration.Underline else null
                    )
                }
            }
            if (sortedData.size > 6) Text(text = "...", style = MaterialTheme.typography.labelSmall)
        }
    }

    if (showPendingBreakdown) {
        PendingSubscriptionsDialog(pendingSubs) { showPendingBreakdown = false }
    }
}

@Composable
fun PendingSubscriptionsDialog(subscriptions: List<Subscription>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upcoming Subscriptions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                subscriptions.sortedBy { it.renewalDate }.forEach { sub ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(sub.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Renewal: ${sub.renewalDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("$${String.format(Locale.US, "%.2f", sub.amount)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Pending", fontWeight = FontWeight.Bold)
                    Text("$${String.format(Locale.US, "%.2f", subscriptions.sumOf { it.amount })}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
