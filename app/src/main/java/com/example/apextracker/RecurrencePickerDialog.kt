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
    onDismiss: () -> Unit,
    onConfirm: (Recurrence) -> Unit
) {
    var frequency by remember { mutableStateOf(RecurrenceFrequency.DAILY) }
    var customDays by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    var endType by remember { mutableStateOf(RecurrenceEndType.NEVER) }
    var occurrences by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Recurrence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Frequency
                ExposedDropdownMenuBox(expanded = false, onExpandedChange = {})
                 {
                    OutlinedTextField(
                        value = frequency.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = false, onDismissRequest = { }) {
                        RecurrenceFrequency.values().forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq.name) },
                                onClick = { frequency = freq }
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
                ExposedDropdownMenuBox(expanded = false, onExpandedChange = {})
                {
                   OutlinedTextField(
                       value = endType.name,
                       onValueChange = {},
                       readOnly = true,
                       label = { Text("Ends") },
                       trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
                       modifier = Modifier.menuAnchor()
                   )
                    ExposedDropdownMenu(expanded = false, onDismissRequest = { }) {
                        RecurrenceEndType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = { endType = type }
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
