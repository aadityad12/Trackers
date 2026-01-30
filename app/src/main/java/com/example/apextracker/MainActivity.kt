package com.example.apextracker

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.apextracker.ui.theme.ApexTrackerTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ApexTrackerTheme {
                BudgetTrackerApp()
            }
        }
    }
}

enum class Screen {
    List, Calendar, Categories
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetTrackerApp(viewModel: BudgetViewModel = viewModel()) {
    val items by viewModel.allItems.collectAsState(initial = emptyList())
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(Screen.List) }

    Scaffold(
        topBar = {
            TopAppBar(title = { 
                Text(when(currentScreen) {
                    Screen.List -> "Budget Tracker"
                    Screen.Calendar -> "Calendar View"
                    Screen.Categories -> "Manage Categories"
                }) 
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == Screen.List,
                    onClick = { currentScreen = Screen.List },
                    icon = { Icon(Icons.Default.List, contentDescription = "List") },
                    label = { Text("List") }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.Calendar,
                    onClick = { currentScreen = Screen.Calendar },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                    label = { Text("Calendar") }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.Categories,
                    onClick = { currentScreen = Screen.Categories },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Categories") },
                    label = { Text("Categories") }
                )
            }
        },
        floatingActionButton = {
            if (currentScreen != Screen.Categories) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentScreen) {
                Screen.List -> BudgetListView(items, categories, onDelete = { viewModel.deleteItem(it) })
                Screen.Calendar -> CalendarView(items, categories)
                Screen.Categories -> CategoriesView(categories, 
                    onAdd = { name, color -> viewModel.addCategory(name, color) },
                    onDelete = { viewModel.deleteCategory(it) }
                )
            }
        }

        if (showAddDialog) {
            AddBudgetItemDialog(
                categories = categories,
                onDismiss = { showAddDialog = false },
                onConfirm = { title, amount, description, date, categoryId ->
                    viewModel.addItem(title, amount, description, date, categoryId)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun BudgetListView(items: List<BudgetItem>, categories: List<Category>, onDelete: (BudgetItem) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val groupedItems = items.groupBy { it.date }
        groupedItems.keys.sortedDescending().forEach { date ->
            val itemsForDate = groupedItems[date] ?: emptyList()
            item {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(itemsForDate) { item ->
                val category = categories.find { it.id == item.categoryId }
                BudgetListItem(item, category, onDelete = { onDelete(item) })
            }
            item {
                val dailyTotal = itemsForDate.sumOf { it.amount }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Daily Total: $${String.format(Locale.US, "%.2f", dailyTotal)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun CategoriesView(categories: List<Category>, onAdd: (String, String) -> Unit, onDelete: (Category) -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Create New Category")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(24.dp).background(Color(android.graphics.Color.parseColor(category.colorHex)), CircleShape))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { onDelete(category) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        AddCategoryDialog(onDismiss = { showAddDialog = false }, onConfirm = onAdd)
    }
}

@Composable
fun AddCategoryDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FF0000") }
    
    val colors = listOf("#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#FFA500", "#800080", "#008000")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category Name") })
                Text("Select Color:")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    colors.take(5).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(android.graphics.Color.parseColor(color)), CircleShape)
                                .border(if (selectedColor == color) 2.dp else 0.dp, Color.Black, CircleShape)
                                .clickable { selectedColor = color }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    colors.drop(5).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(android.graphics.Color.parseColor(color)), CircleShape)
                                .border(if (selectedColor == color) 2.dp else 0.dp, Color.Black, CircleShape)
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, selectedColor); onDismiss() }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CalendarView(items: List<BudgetItem>, categories: List<Category>) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDayItems by remember { mutableStateOf<List<BudgetItem>?>(null) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value % 7
    val days = (1..daysInMonth).toList()
    val paddingDays = (0 until firstDayOfMonth).toList()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Text("<", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Text(">", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(paddingDays) {
                Box(modifier = Modifier.aspectRatio(1f))
            }
            items(days) { day ->
                val date = currentMonth.atDay(day)
                val itemsForDay = items.filter { it.date == date }
                val totalSpent = itemsForDay.sumOf { it.amount }

                Card(
                    modifier = Modifier
                        .aspectRatio(0.8f)
                        .clickable {
                            if (itemsForDay.isNotEmpty()) {
                                selectedDayItems = itemsForDay
                                selectedDate = date
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (date == LocalDate.now()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(2.dp).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = day.toString(), style = MaterialTheme.typography.bodyMedium)
                        if (totalSpent > 0) {
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", totalSpent)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedDayItems != null && selectedDate != null) {
        DayBreakdownDialog(
            date = selectedDate!!,
            items = selectedDayItems!!,
            categories = categories,
            onDismiss = {
                selectedDayItems = null
                selectedDate = null
            }
        )
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (category != null) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(android.graphics.Color.parseColor(category.colorHex)), CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (!item.description.isNullOrBlank()) {
                                Text(text = item.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", item.amount)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider()
                }
                val total = items.sumOf { it.amount }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", total)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun BudgetListItem(item: BudgetItem, category: Category?, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (category != null) Color(android.graphics.Color.parseColor(category.colorHex)).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (category != null) {
                            Box(modifier = Modifier.size(12.dp).background(Color(android.graphics.Color.parseColor(category.colorHex)), CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = item.title, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", item.amount)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!item.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (category != null) {
                    Text(text = category.name, style = MaterialTheme.typography.labelSmall, color = Color(android.graphics.Color.parseColor(category.colorHex)))
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete, 
                    contentDescription = "Delete Item",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetItemDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String?, LocalDate, Long?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var expanded by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            date = LocalDate.of(year, month + 1, dayOfMonth)
        },
        date.year,
        date.monthValue - 1,
        date.dayOfMonth
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Budget Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { 
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amount = it
                        }
                    },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "No Category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No Category") },
                            onClick = { selectedCategory = null; expanded = false }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(12.dp).background(Color(android.graphics.Color.parseColor(category.colorHex)), CircleShape))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(category.name)
                                    }
                                },
                                onClick = { selectedCategory = category; expanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank()) {
                        onConfirm(title, amountDouble, description.ifBlank { null }, date, selectedCategory?.id)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
