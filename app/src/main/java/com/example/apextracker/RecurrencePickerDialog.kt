package com.example.apextracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrencePickerDialog(
    initialRecurrence: Recurrence? = null,
    onDismiss: () -> Unit,
    onConfirm: (Recurrence) -> Unit
) {
    var frequency by remember { mutableStateOf(initialRecurrence?.frequency ?: RecurrenceFrequency.DAILY) }
    var customDays by remember { mutableStateOf(initialRecurrence?.customDays ?: emptySet<DayOfWeek>()) }
    var endType by remember { mutableStateOf(initialRecurrence?.endType ?: RecurrenceEndType.NEVER) }
    var occurrences by remember { mutableStateOf(initialRecurrence?.endOccurrences ?: 1) }
    var frequencyExpanded by remember { mutableStateOf(false) }
    var endTypeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Recurrence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Frequency
                ExposedDropdownMenuBox(expanded = frequencyExpanded, onExpandedChange = { frequencyExpanded = it })
                 {
                    OutlinedTextField(
                        value = frequency.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = frequencyExpanded, onDismissRequest = { frequencyExpanded = false }) {
                        RecurrenceFrequency.values().forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq.name) },
                                onClick = { frequency = freq; frequencyExpanded = false }
                            )
                        }
                    }
                }

                if (frequency == RecurrenceFrequency.CUSTOM) {
                    Text("Repeat on:")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.values().forEach { day ->
                            val isSelected = customDays.contains(day)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
                                    .clickable { 
                                        customDays = if (isSelected) customDays - day else customDays + day
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                            }
                        }
                    }
                }

                // End Type
                ExposedDropdownMenuBox(expanded = endTypeExpanded, onExpandedChange = { endTypeExpanded = it })
                {
                   OutlinedTextField(
                       value = endType.name,
                       onValueChange = {},
                       readOnly = true,
                       label = { Text("Ends") },
                       trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endTypeExpanded) },
                       modifier = Modifier.menuAnchor()
                   )
                    ExposedDropdownMenu(expanded = endTypeExpanded, onDismissRequest = { endTypeExpanded = false }) {
                        RecurrenceEndType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = { endType = type; endTypeExpanded = false }
                            )
                        }
                    }
                }

                if (endType == RecurrenceEndType.AFTER_OCCURRENCES) {
                    OutlinedTextField(
                        value = occurrences.toString(),
                        onValueChange = { occurrences = it.toIntOrNull() ?: 1 },
                        label = { Text("After Occurrences") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val recurrence = Recurrence(
                    frequency = frequency,
                    customDays = if (frequency == RecurrenceFrequency.CUSTOM) customDays else null,
                    endType = endType,
                    endOccurrences = if (endType == RecurrenceEndType.AFTER_OCCURRENCES) occurrences else null
                )
                onConfirm(recurrence)
            }) {
                Text("Done")
            }
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
