package com.example.apextracker

import android.app.DatePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BudgetListItem(
    item: BudgetItem,
    category: Category?,
    onClick: () -> Unit,
    isPending: Boolean = false
) {
    val catColor = category?.let { Color(android.graphics.Color.parseColor(it.colorHex)) } ?: MaterialTheme.colorScheme.surface
    val displayColor = if (isPending) catColor.copy(alpha = 0.4f) else catColor

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = tween(100),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (category != null) displayColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                BudgetListItemHeader(item, category, isPending)
                if (!item.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.description, 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (category != null) {
                    Text(
                        text = if (isPending) "Pending: ${category.name}" else category.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (category.id == -1L) Color(0xFFB8860B) else displayColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetListItemHeader(item: BudgetItem, category: Category?, isPending: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (category != null) {
                val color = Color(android.graphics.Color.parseColor(category.colorHex))
                Box(modifier = Modifier.size(12.dp).background(if (isPending) color.copy(alpha = 0.4f) else color, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = item.title, 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "$${String.format(Locale.US, "%.2f", item.amount)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (isPending) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
        )
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
                Text(title, fontWeight = FontWeight.Bold)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = itemTitle, onValueChange = { itemTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                OutlinedTextField(value = amount, onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                CategoryDropdown(categories, selectedCategory, expanded, onExpandedChange = { expanded = it }, onCategorySelected = { selectedCategory = it; expanded = false })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (Optional)") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                Button(
                    onClick = { datePickerDialog.show() }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    shape = MaterialTheme.shapes.medium
                ) { 
                    Text("Date: ${date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}") 
                }
            }
        },
        confirmButton = { 
            Button(
                onClick = { if (itemTitle.isNotBlank()) onConfirm(itemTitle, amount.toDoubleOrNull() ?: 0.0, description.ifBlank { null }, date, selectedCategory?.id) },
                shape = MaterialTheme.shapes.medium
            ) { 
                Text("Save") 
            } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel") 
            } 
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(categories: List<Category>, selectedCategory: Category?, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onCategorySelected: (Category?) -> Unit) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = selectedCategory?.name ?: "No Category", 
            onValueChange = {}, 
            readOnly = true, 
            label = { Text("Category") }, 
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, 
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
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
