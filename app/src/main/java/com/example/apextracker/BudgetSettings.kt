package com.example.apextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BudgetSettingsView(categories: List<Category>, viewModel: BudgetViewModel) {
    var activeSubScreen by remember { mutableStateOf<String?>(null) }

    when (activeSubScreen) {
        "categories" -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsHeader("Manage Categories") { activeSubScreen = null }
                CategoriesView(
                    categories = categories,
                    onAdd = { name, color -> viewModel.addCategory(name, color) },
                    onUpdate = { viewModel.updateCategory(it) },
                    onDelete = { viewModel.deleteCategory(it) }
                )
            }
        }
        "subscriptions" -> {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsHeader("Manage Subscriptions") { activeSubScreen = null }
                SubscriptionsView(viewModel)
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetSettingsItem("Manage Categories") { activeSubScreen = "categories" }
                BudgetSettingsItem("Manage Subscriptions") { activeSubScreen = "subscriptions" }
            }
        }
    }
}

@Composable
fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
    }
    HorizontalDivider()
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
                    onEdit = { categoryToEdit = category }
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
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
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
    onConfirm: (String, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
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
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
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
fun SubscriptionsView(viewModel: BudgetViewModel) {
    val subscriptions by viewModel.allSubscriptions.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var subToEdit by remember { mutableStateOf<Subscription?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Add New Subscription")
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subscriptions) { sub ->
                SubscriptionItem(sub) { subToEdit = sub }
            }
        }
    }

    if (showAddDialog) {
        SubscriptionDialog(
            title = "New Subscription",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, amount, date, notes ->
                viewModel.addSubscription(name, amount, date, notes)
            }
        )
    }

    if (subToEdit != null) {
        SubscriptionDialog(
            title = "Edit Subscription",
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(subscription.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Renews: ${subscription.renewalDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}", style = MaterialTheme.typography.bodySmall)
            }
            Text("$${String.format(Locale.US, "%.2f", subscription.amount)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth()) { Text("Next Renewal: ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}") }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (Optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, amount.toDoubleOrNull() ?: 0.0, date, notes.ifBlank { null }); onDismiss() }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
