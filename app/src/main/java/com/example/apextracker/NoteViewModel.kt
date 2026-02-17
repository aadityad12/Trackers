package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val noteDao = AppDatabase.getDatabase(application).noteDao()
    private val noteSettings = NoteSettings(application)

    val activeNotes: StateFlow<List<Note>> = noteDao.getAllActiveNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedNotes: StateFlow<List<Note>> = noteDao.getDeletedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recycleBinRetentionHours = noteSettings.recycleBinRetentionHours

    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            noteDao.insert(Note(title = title, content = content, createdAt = now, modifiedAt = now))
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            noteDao.update(note.copy(modifiedAt = LocalDateTime.now()))
        }
    }

    fun moveToRecycleBin(note: Note) {
        viewModelScope.launch {
            noteDao.update(note.copy(isDeleted = true, deletedAt = LocalDateTime.now()))
        }
    }

    fun restoreNote(note: Note) {
        viewModelScope.launch {
            noteDao.update(note.copy(isDeleted = false, deletedAt = null, modifiedAt = LocalDateTime.now()))
        }
    }

    fun deletePermanently(note: Note) {
        viewModelScope.launch {
            noteDao.deletePermanently(note)
        }
    }

    fun setRetentionHours(hours: Int) {
        viewModelScope.launch {
            noteSettings.setRecycleBinRetentionHours(hours)
        }
    }

    fun cleanUpRecycleBin() {
        viewModelScope.launch {
            val hours = recycleBinRetentionHours.first()
            val threshold = LocalDateTime.now().minusHours(hours.toLong())
            noteDao.deleteOldNotes(threshold)
        }
    }
}
