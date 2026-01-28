package com.example.apextracker

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    List, Calendar
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetTrackerApp(viewModel: BudgetViewModel = viewModel()) {
    val items by viewModel.allItems.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(Screen.List) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (currentScreen == Screen.List) "Budget Tracker" else "Calendar View") })
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
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (currentScreen == Screen.List) {
                BudgetListView(items)
            } else {
                CalendarView(items)
            }
        }

        if (showAddDialog) {
            AddBudgetItemDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, amount, description, date ->
                    viewModel.addItem(title, amount, description, date)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun BudgetListView(items: List<BudgetItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // items is already sorted by date DESC from the DAO
        val groupedItems = items.groupBy { it.date }
        // Sorting keys descending ensures newest date is first
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
                BudgetListItem(item)
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
fun CalendarView(items: List<BudgetItem>) {
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
            onDismiss = {
                selectedDayItems = null
                selectedDate = null
            }
        )
    }
}

@Composable
fun DayBreakdownDialog(date: LocalDate, items: List<BudgetItem>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Breakdown for ${date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
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
fun BudgetListItem(item: BudgetItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = item.title, style = MaterialTheme.typography.titleLarge)
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
        }
    }
}

@Composable
fun AddBudgetItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String?, LocalDate) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    
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
                        onConfirm(title, amountDouble, description.ifBlank { null }, date)
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
