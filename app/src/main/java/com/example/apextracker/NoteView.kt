package com.example.apextracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteView(onBackToMenu: () -> Unit, viewModel: NoteViewModel = viewModel()) {
    var showRecycleBin by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<Note?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val activeNotes by viewModel.activeNotes.collectAsState()
    val deletedNotes by viewModel.deletedNotes.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.cleanUpRecycleBin()
    }

    if (noteToEdit != null) {
        NoteEditor(
            note = noteToEdit!!,
            onDismiss = { noteToEdit = null },
            onSave = { title, content ->
                if (noteToEdit!!.id == 0L) {
                    viewModel.addNote(title, content)
                } else {
                    viewModel.updateNote(noteToEdit!!.copy(title = title, content = content))
                }
                noteToEdit = null
            }
        )
    } else if (showRecycleBin) {
        RecycleBinView(
            notes = deletedNotes,
            onBack = { showRecycleBin = false },
            onRestore = { viewModel.restoreNote(it) },
            onDeletePermanently = { viewModel.deletePermanently(it) }
        )
    } else if (showSettings) {
        NoteSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettings = false }
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Notes", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackToMenu) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showRecycleBin = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Recycle Bin")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { noteToEdit = Note(title = "", content = "") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
                }
            }
        ) { innerPadding ->
            if (activeNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text("No notes yet", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeNotes) { note ->
                        NoteCard(
                            note = note,
                            onClick = { noteToEdit = note },
                            onDelete = { viewModel.moveToRecycleBin(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(note: Note, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                }
            }
            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Modified: ${note.modifiedAt.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(note: Note, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var contentValue by remember {
        mutableStateOf(TextFieldValue(note.content, selection = TextRange(note.content.length)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (note.id == 0L) "New Note" else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(title, contentValue.text) }) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = contentValue,
                onValueChange = { newValue ->
                    contentValue = handleNoteContentChange(newValue, contentValue)
                },
                placeholder = { Text("Start typing...") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            
            // Helper bar for lists
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InputToolButton(icon = Icons.AutoMirrored.Filled.List, label = "Bullet") {
                    val prefix = if (contentValue.text.isEmpty() || contentValue.text.endsWith("\n")) "• " else "\n• "
                    val newText = contentValue.text.substring(0, contentValue.selection.start) + prefix + contentValue.text.substring(contentValue.selection.end)
                    val newSelection = TextRange(contentValue.selection.start + prefix.length)
                    contentValue = TextFieldValue(newText, newSelection)
                }
                InputToolButton(icon = Icons.AutoMirrored.Filled.FormatIndentIncrease, label = "Subpoint") {
                    val prefix = if (contentValue.text.isEmpty() || contentValue.text.endsWith("\n")) "  ◦ " else "\n  ◦ "
                    val newText = contentValue.text.substring(0, contentValue.selection.start) + prefix + contentValue.text.substring(contentValue.selection.end)
                    val newSelection = TextRange(contentValue.selection.start + prefix.length)
                    contentValue = TextFieldValue(newText, newSelection)
                }
            }
        }
    }
}

private fun handleNoteContentChange(
    newValue: TextFieldValue,
    oldValue: TextFieldValue
): TextFieldValue {
    if (newValue.text.length > oldValue.text.length) {
        val addedCharIndex = newValue.selection.start - 1
        if (addedCharIndex >= 0 && newValue.text[addedCharIndex] == '\n') {
            // Newline was added
            val textBeforeCursor = newValue.text.substring(0, addedCharIndex)
            val lastLine = textBeforeCursor.split("\n").lastOrNull() ?: ""
            
            val prefix = when {
                lastLine.startsWith("• ") -> "• "
                lastLine.startsWith("  ◦ ") -> "  ◦ "
                else -> null
            }
            
            if (prefix != null) {
                val newText = newValue.text.substring(0, newValue.selection.start) + prefix + newValue.text.substring(newValue.selection.start)
                val newSelection = TextRange(newValue.selection.start + prefix.length)
                return TextFieldValue(newText, newSelection)
            }
        }
    } else if (newValue.text.length < oldValue.text.length) {
        // Deletion
        val oldSelection = oldValue.selection.start
        val textBeforeOldCursor = oldValue.text.substring(0, oldSelection)
        val lastLine = textBeforeOldCursor.split("\n").lastOrNull() ?: ""
        
        if ((lastLine == "• " || lastLine == "  ◦ ") && newValue.selection.start < oldSelection) {
            // If deleting from an empty bullet line, remove the whole bullet prefix
            val startOfLine = oldSelection - lastLine.length
            val newText = oldValue.text.substring(0, startOfLine) + oldValue.text.substring(oldSelection)
            return TextFieldValue(newText, TextRange(startOfLine))
        }
    }
    return newValue
}

@Composable
fun InputToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinView(
    notes: List<Note>,
    onBack: () -> Unit,
    onRestore: (Note) -> Unit,
    onDeletePermanently: (Note) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Recycle bin is empty", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notes) { note ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(note.title.ifBlank { "Untitled" }, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onRestore(note) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Restore", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { onDeletePermanently(note) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Delete Forever", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteSettingsDialog(viewModel: NoteViewModel, onDismiss: () -> Unit) {
    val retentionHours by viewModel.recycleBinRetentionHours.collectAsState(initial = 72)
    var sliderValue by remember { mutableFloatStateOf(retentionHours.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note Settings") },
        text = {
            Column {
                Text("Recycle Bin Retention")
                Text(
                    "Notes will be permanently deleted after ${sliderValue.toInt()} hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 1f..168f, // 1 hour to 1 week
                    steps = 167
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1h", style = MaterialTheme.typography.labelSmall)
                    Text("72h (Default)", style = MaterialTheme.typography.labelSmall)
                    Text("168h", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.setRetentionHours(sliderValue.toInt())
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
