package com.example.apextracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

internal val bulletSequence = listOf("• ", "  ◦ ", "    ▪ ")
internal val bulletRegex = Regex("^(\\s*[•◦▪])\\s")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteView(onBackToMenu: () -> Unit, viewModel: NoteViewModel = viewModel()) {
    var showRecycleBin by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<Note?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    val activeNotes by viewModel.activeNotes.collectAsState()
    val filteredNotes by viewModel.filteredNotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val deletedNotes by viewModel.deletedNotes.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.cleanUpRecycleBin()
    }

    if (noteToEdit != null) {
        NoteEditor(
            note = noteToEdit!!,
            onDismiss = { noteToEdit = null },
            onTogglePin = {
                viewModel.togglePin(noteToEdit!!)
                noteToEdit = noteToEdit!!.copy(isPinned = !noteToEdit!!.isPinned)
            },
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
                    title = {
                        if (isSearching) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        } else {
                            Text(stringResource(R.string.notes_title), fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (isSearching) { isSearching = false; viewModel.setSearchQuery("") } else onBackToMenu() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        if (!isSearching) {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                            }
                            IconButton(onClick = { showRecycleBin = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.notes_recycle_bin))
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.menu_settings))
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { noteToEdit = Note(title = "", content = "") }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_note))
                }
            }
        ) { innerPadding ->
            if (activeNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.notes_empty), color = MaterialTheme.colorScheme.outline)
                }
            } else if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.notes_search_no_results, searchQuery), color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotes) { note ->
                        NoteCard(
                            note = note,
                            onClick = { noteToEdit = note },
                            onDelete = { viewModel.moveToRecycleBin(note) },
                            onTogglePin = { viewModel.togglePin(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(note: Note, onClick: () -> Unit, onDelete: () -> Unit, onTogglePin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = note.title.ifBlank { stringResource(R.string.notes_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(if (note.isPinned) R.string.cd_unpin_note else R.string.cd_pin_note),
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
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
                text = stringResource(R.string.notes_modified_prefix, note.modifiedAt.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(note: Note, onDismiss: () -> Unit, onTogglePin: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var contentValue by remember {
        mutableStateOf(TextFieldValue(note.content, selection = TextRange(note.content.length)))
    }
    val context = LocalContext.current
    val untitledLabel = stringResource(R.string.notes_untitled)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (note.id == 0L) R.string.notes_new_title else R.string.notes_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    // Shares the current in-editor title/content, so unsaved edits are included.
                    IconButton(onClick = { shareNote(context, title, contentValue.text, untitledLabel) }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_share_note))
                    }
                    // Pinning only applies to an already-saved note (an unsaved new note has no row to update).
                    if (note.id != 0L) {
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(if (note.isPinned) R.string.cd_unpin_note else R.string.cd_pin_note),
                                tint = if (note.isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                    TextButton(onClick = { onSave(title, contentValue.text) }) {
                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(stringResource(R.string.notes_placeholder_title)) },
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
                placeholder = { Text(stringResource(R.string.notes_placeholder_content)) },
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
                InputToolButton(icon = Icons.AutoMirrored.Filled.List, label = stringResource(R.string.notes_tool_bullet)) {
                    contentValue = modifyCurrentLine(contentValue) { line ->
                        val match = bulletRegex.find(line)
                        if (match != null && match.value == bulletSequence[0]) {
                            line.substring(match.value.length)
                        } else if (match != null) {
                            bulletSequence[0] + line.substring(match.value.length)
                        } else {
                            bulletSequence[0] + line
                        }
                    }
                }
                InputToolButton(icon = Icons.AutoMirrored.Filled.FormatIndentIncrease, label = stringResource(R.string.notes_tool_indent)) {
                    contentValue = modifyCurrentLine(contentValue) { line ->
                        val match = bulletRegex.find(line)
                        if (match != null) {
                            val currentIndex = bulletSequence.indexOf(match.value)
                            if (currentIndex != -1 && currentIndex < bulletSequence.size - 1) {
                                bulletSequence[currentIndex + 1] + line.substring(match.value.length)
                            } else line
                        } else {
                            // Indent only applies to already-bulleted lines; a plain line is left untouched.
                            line
                        }
                    }
                }
                InputToolButton(icon = Icons.AutoMirrored.Filled.FormatIndentDecrease, label = stringResource(R.string.notes_tool_outdent)) {
                    contentValue = modifyCurrentLine(contentValue) { line ->
                        val match = bulletRegex.find(line)
                        if (match != null) {
                            val currentIndex = bulletSequence.indexOf(match.value)
                            if (currentIndex > 0) {
                                bulletSequence[currentIndex - 1] + line.substring(match.value.length)
                            } else if (currentIndex == 0) {
                                line.substring(match.value.length)
                            } else line
                        } else line
                    }
                }
            }
        }
    }
}

internal fun modifyCurrentLine(value: TextFieldValue, action: (String) -> String): TextFieldValue {
    val text = value.text
    val selection = value.selection
    val lineStart = text.lastIndexOf('\n', selection.start - 1).let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', selection.start).let { if (it == -1) text.length else it }
    
    val currentLine = text.substring(lineStart, lineEnd)
    val newLine = action(currentLine)
    
    val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
    val diff = newLine.length - currentLine.length
    val newCursor = (selection.start + diff).coerceIn(lineStart, lineStart + newLine.length)
    return TextFieldValue(newText, TextRange(newCursor))
}

internal fun handleNoteContentChange(
    newValue: TextFieldValue,
    oldValue: TextFieldValue
): TextFieldValue {
    if (newValue.text.length == oldValue.text.length + 1) {
        val cursor = newValue.selection.start
        if (cursor > 0 && newValue.text[cursor - 1] == '\n') {
            val textBeforeNewLine = newValue.text.substring(0, cursor - 1)
            val lastLine = textBeforeNewLine.substringAfterLast('\n')
            
            val match = bulletRegex.find(lastLine)
            if (match != null) {
                val prefix = match.value
                if (lastLine.trim() == prefix.trim()) {
                    // Empty bullet line - Outdent or Clear
                    val currentIndex = bulletSequence.indexOf(prefix)
                    val lineStart = cursor - 1 - lastLine.length
                    if (currentIndex > 0) {
                        val newPrefix = bulletSequence[currentIndex - 1]
                        return TextFieldValue(
                            newValue.text.substring(0, lineStart) + newPrefix + newValue.text.substring(cursor),
                            TextRange(lineStart + newPrefix.length)
                        )
                    } else {
                        return TextFieldValue(
                            newValue.text.substring(0, lineStart) + "\n" + newValue.text.substring(cursor),
                            TextRange(lineStart + 1)
                        )
                    }
                } else {
                    // Continue Bullet
                    return TextFieldValue(
                        newValue.text.substring(0, cursor) + prefix + newValue.text.substring(cursor),
                        TextRange(cursor + prefix.length)
                    )
                }
            }
        }
    } else if (newValue.text.length == oldValue.text.length - 1) {
        val oldCursor = oldValue.selection.start
        val textBefore = oldValue.text.substring(0, oldCursor)
        val lastLine = textBefore.substringAfterLast('\n')
        val match = bulletRegex.find(lastLine)
        if (match != null && match.value == lastLine && newValue.selection.start == oldCursor - 1) {
            val lineStart = oldCursor - lastLine.length
            return TextFieldValue(
                oldValue.text.substring(0, lineStart) + oldValue.text.substring(oldCursor),
                TextRange(lineStart)
            )
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
                title = { Text(stringResource(R.string.notes_recycle_bin)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.notes_recycle_empty), color = MaterialTheme.colorScheme.outline)
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
                            Text(note.title.ifBlank { stringResource(R.string.notes_untitled) }, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onRestore(note) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(stringResource(R.string.notes_restore), fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { onDeletePermanently(note) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.notes_delete_forever), fontSize = 12.sp)
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
    val context = LocalContext.current
    val securitySettings = remember { SecuritySettings(context) }
    val notesLocked by securitySettings.notesLockEnabled.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_settings_title)) },
        text = {
            Column {
                Text(stringResource(R.string.notes_retention))
                Text(
                    stringResource(R.string.notes_deleted_after, sliderValue.toInt()),
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
                    Text(stringResource(R.string.notes_retention_1h), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.notes_retention_72h), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.notes_retention_168h), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                ModuleLockSetting(
                    checked = notesLocked,
                    titleRes = R.string.security_lock_notes_title,
                    onCheckedChange = { scope.launch { securitySettings.setNotesLock(it) } }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.setRetentionHours(sliderValue.toInt())
                onDismiss()
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
