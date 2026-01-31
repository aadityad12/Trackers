package com.example.apextracker

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class BudgetScreen {
    Overview, Calendar, Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetTrackerApp(onBackToMenu: () -> Unit, viewModel: BudgetViewModel = viewModel()) {
    val items by viewModel.allItems.collectAsState(initial = emptyList())
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
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
                    IconButton(onClick = onBackToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Menu")
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
                BudgetScreen.Overview -> OverviewView(items, categories, onEdit = { itemToEdit = it })
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
fun OverviewView(items: List<BudgetItem>, categories: List<Category>, onEdit: (BudgetItem) -> Unit) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    
    val availableMonths = items.map { YearMonth.from(it.date) }.distinct().sortedDescending()
    val monthToDisplay = if (availableMonths.contains(selectedMonth)) selectedMonth 
                         else availableMonths.firstOrNull() ?: selectedMonth

    val monthItems = items.filter { YearMonth.from(it.date) == monthToDisplay }
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
                ExpenditureCard(totalExpenditure, monthItems, categories)
            }

            if (monthItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                val itemsByCategory = monthItems.groupBy { it.categoryId }
                itemsByCategory.forEach { (catId, catItems) ->
                    val category = categories.find { it.id == catId }
                    item {
                        ExpandableCategorySection(
                            category = category,
                            items = catItems,
                            onEdit = onEdit
                        )
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
fun ExpenditureCard(totalExpenditure: Double, monthItems: List<BudgetItem>, categories: List<Category>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
            
            if (monthItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                ExpensePieChart(monthItems, categories)
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
fun ExpandableCategorySection(category: Category?, items: List<BudgetItem>, onEdit: (BudgetItem) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val catColor = category?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: Color.Gray

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            colors = CardDefaults.cardColors(containerColor = catColor.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(catColor, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = category?.name ?: "Uncategorized",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", items.sumOf { it.amount })}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }
        }
        
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                items.sortedByDescending { it.date }.forEach { item ->
                    BudgetListItem(item, category, onClick = { onEdit(item) })
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun ExpensePieChart(items: List<BudgetItem>, categories: List<Category>) {
    val itemsByCategory = items.groupBy { it.categoryId }
    val total = items.sumOf { it.amount }
    
    val chartData = itemsByCategory.map { (catId, catItems) ->
        val category = categories.find { it.id == catId }
        val color = category?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: Color.Gray
        val amount = catItems.sumOf { it.amount }
        Triple(category?.name ?: "Uncategorized", amount.toFloat(), color)
    }.sortedByDescending { it.second }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            var startAngle = -90f
            chartData.forEach { (_, amount, color) ->
                val sweepAngle = (amount / total.toFloat()) * 360f
                drawArc(color = color, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true)
                drawArc(color = Color.White, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = true, style = Stroke(width = 2.dp.toPx()))
                startAngle += sweepAngle
            }
            drawCircle(color = Color.White, radius = size.minDimension / 4)
        }
        
        Spacer(modifier = Modifier.width(24.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chartData.take(5).forEach { (name, amount, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$name (${String.format(Locale.US, "%.0f%%", (amount / total.toFloat()) * 100)})",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (chartData.size > 5) Text(text = "...", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun BudgetSettingsView(categories: List<Category>, viewModel: BudgetViewModel) {
    var showCategories by remember { mutableStateOf(false) }

    if (showCategories) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showCategories = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(text = "Manage Categories", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()
            CategoriesView(
                categories = categories,
                onAdd = { name, color -> viewModel.addCategory(name, color) },
                onUpdate = { viewModel.updateCategory(it) },
                onDelete = { viewModel.deleteCategory(it) }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            BudgetSettingsItem("Manage Categories") { showCategories = true }
        }
    }
}

@Composable
fun BudgetSettingsItem(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
    onAdd: (String, String) -> Unit, 
    onUpdate: (Category) -> Unit,
    onDelete: (Category) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Create New Category")
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                CategoryItem(
                    category = category, 
                    onEdit = { categoryToEdit = category },
                    onDelete = { onDelete(category) }
                )
            }
        }
    }

    if (showAddDialog) {
        CategoryDialog(
            title = "New Category",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, color -> onAdd(name, color) }
        )
    }
    
    if (categoryToEdit != null) {
        CategoryDialog(
            title = "Edit Category",
            initialName = categoryToEdit!!.name,
            initialColor = categoryToEdit!!.colorHex,
            onDismiss = { categoryToEdit = null },
            onConfirm = { name, color ->
                onUpdate(categoryToEdit!!.copy(name = name, colorHex = color))
                categoryToEdit = null
            }
        )
    }
}

@Composable
fun CategoryItem(category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(24.dp).background(Color(android.graphics.Color.parseColor(category.colorHex)), CircleShape))
                Spacer(modifier = Modifier.width(12.dp))
                Text(category.name, style = MaterialTheme.typography.bodyLarge)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun CategoryDialog(
    title: String,
    initialName: String = "",
    initialColor: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    // Gmail-inspired colors (24 colors)
    val colors = listOf(
        "#ac725e", "#d06b64", "#f83a22", "#fa573c", "#ff7537", "#ffad46",
        "#42d692", "#16a765", "#7bd148", "#b3dc6c", "#fbe983", "#fad165",
        "#92e1c0", "#9fe1e7", "#9fc6e7", "#4986e7", "#9a9cff", "#b99aff",
        "#c2c2c2", "#cabdbf", "#cca6ac", "#f691b2", "#cd74e6", "#a47ae2"
    )
    var selectedColor by remember { mutableStateOf(initialColor ?: colors[15]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Category Name") }, 
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Select Color:")
                ColorGrid(colors, selectedColor) { selectedColor = it }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, selectedColor); onDismiss() }) { 
                Text(if (initialName.isEmpty()) "Create" else "Save") 
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ColorGrid(colors: List<String>, selectedColor: String, onColorSelected: (String) -> Unit) {
    val columns = 6
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.chunked(columns).forEach { rowColors ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(android.graphics.Color.parseColor(color)), CircleShape)
                            .border(
                                width = if (selectedColor == color) 2.dp else 1.dp,
                                color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetCalendarView(items: List<BudgetItem>, categories: List<Category>) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDayItems by remember { mutableStateOf<List<BudgetItem>?>(null) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value % 7
    val days = (1..daysInMonth).toList()
    val paddingDays = (0 until firstDayOfMonth).toList()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        BudgetMonthSelector(currentMonth = currentMonth, onMonthChange = { currentMonth = it })
        Spacer(modifier = Modifier.height(16.dp))
        WeekdayHeaders()
        Spacer(modifier = Modifier.height(8.dp))
        CalendarGrid(days, paddingDays, currentMonth, items, onDayClick = { date, dayItems ->
            selectedDate = date
            selectedDayItems = dayItems
        })
    }

    if (selectedDayItems != null && selectedDate != null) {
        DayBreakdownDialog(date = selectedDate!!, items = selectedDayItems!!, categories = categories, onDismiss = {
            selectedDayItems = null
            selectedDate = null
        })
    }
}

@Composable
fun BudgetMonthSelector(currentMonth: YearMonth, onMonthChange: (YearMonth) -> Unit) {
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
fun WeekdayHeaders() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
            Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CalendarGrid(days: List<Int>, paddingDays: List<Int>, currentMonth: YearMonth, items: List<BudgetItem>, onDayClick: (LocalDate, List<BudgetItem>) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(paddingDays) { Box(modifier = Modifier.aspectRatio(1f)) }
        items(days) { day ->
            val date = currentMonth.atDay(day)
            val itemsForDay = items.filter { it.date == date }
            val totalSpent = itemsForDay.sumOf { it.amount }
            CalendarDayCard(day, date, totalSpent, onClick = { onDayClick(date, itemsForDay) })
        }
    }
}

@Composable
fun CalendarDayCard(day: Int, date: LocalDate, totalSpent: Double, onClick: () -> Unit) {
    Card(
        modifier = Modifier.aspectRatio(0.8f).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (date == LocalDate.now()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(2.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = day.toString(), style = MaterialTheme.typography.bodyMedium)
            if (totalSpent > 0) {
                Text(text = "$${String.format(Locale.US, "%.2f", totalSpent)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun DayBreakdownDialog(date: LocalDate, items: List<BudgetItem>, categories: List<Category>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Breakdown for ${date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    val category = categories.find { it.id == item.categoryId }
                    DayBreakdownItem(item, category)
                    HorizontalDivider()
                }
                TotalRow(items.sumOf { it.amount })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun DayBreakdownItem(item: BudgetItem, category: Category?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (category != null) {
                    Box(modifier = Modifier.size(8.dp).background(Color(android.graphics.Color.parseColor(category.colorHex)), CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
            }
            if (!item.description.isNullOrBlank()) Text(text = item.description, style = MaterialTheme.typography.bodySmall)
        }
        Text(text = "$${String.format(Locale.US, "%.2f", item.amount)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TotalRow(total: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = "$${String.format(Locale.US, "%.2f", total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun BudgetListItem(item: BudgetItem, category: Category?, onClick: () -> Unit) {
    val catColor = category?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = if (category != null) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BudgetListItemHeader(item, category)
                if (!item.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (category != null) {
                    Text(text = category.name, style = MaterialTheme.typography.labelSmall, color = catColor)
                }
            }
        }
    }
}

@Composable
fun BudgetListItemHeader(item: BudgetItem, category: Category?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (category != null) {
                Box(modifier = Modifier.size(12.dp).background(Color(android.graphics.Color.parseColor(category.colorHex)), CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = item.title, style = MaterialTheme.typography.titleLarge)
        }
        Text(text = "$${String.format(Locale.US, "%.2f", item.amount)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetItemDialog(
    title: String,
    initialTitle: String = "",
    initialAmount: String = "",
    initialDescription: String = "",
    initialDate: LocalDate = LocalDate.now(),
    initialCategoryId: Long? = null,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String?, LocalDate, Long?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var itemTitle by remember { mutableStateOf(initialTitle) }
    var amount by remember { mutableStateOf(initialAmount) }
    var description by remember { mutableStateOf(initialDescription) }
    var date by remember { mutableStateOf(initialDate) }
    var selectedCategory by remember { mutableStateOf(categories.find { it.id == initialCategoryId }) }
    var expanded by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val datePickerDialog = remember {
        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, date.year, date.monthValue - 1, date.dayOfMonth)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = itemTitle, onValueChange = { itemTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                CategoryDropdown(categories, selectedCategory, expanded, onExpandedChange = { expanded = it }, onCategorySelected = { selectedCategory = it; expanded = false })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (Optional)") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth()) { Text("Date: ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}") }
            }
        },
        confirmButton = { Button(onClick = { if (itemTitle.isNotBlank()) onConfirm(itemTitle, amount.toDoubleOrNull() ?: 0.0, description.ifBlank { null }, date, selectedCategory?.id) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(categories: List<Category>, selectedCategory: Category?, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onCategorySelected: (Category?) -> Unit) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(value = selectedCategory?.name ?: "No Category", onValueChange = {}, readOnly = true, label = { Text("Category") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(text = { Text("No Category") }, onClick = { onCategorySelected(null) })
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(Color(android.graphics.Color.parseColor(category.colorHex)), CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(category.name)
                        }
                    },
                    onClick = { onCategorySelected(category) }
                )
            }
        }
    }
}
