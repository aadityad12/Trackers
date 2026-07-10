package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "NoteViewModel"
    }

    private val noteDao = AppDatabase.getDatabase(application).noteDao()
    private val noteSettings = NoteSettings(application)
    private val firebaseManager = FirebaseManager(application)

    val activeNotes: StateFlow<List<Note>> = noteDao.getAllActiveNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedNotes: StateFlow<List<Note>> = noteDao.getDeletedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recycleBinRetentionHours = noteSettings.recycleBinRetentionHours

    fun addNote(title: String, content: String) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val note = Note(
                title = title,
                content = content,
                createdAt = now,
                modifiedAt = now,
                cloudId = UUID.randomUUID().toString()
            )
            noteDao.insert(note)
            safeCloudCall(TAG, "push note") {
                firebaseManager.pushNote(note)
            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(
                modifiedAt = LocalDateTime.now(),
                cloudId = note.cloudId.ifEmpty { UUID.randomUUID().toString() }
            )
            noteDao.update(updated)
            safeCloudCall(TAG, "update note") {
                firebaseManager.pushNote(updated)
            }
        }
    }

    fun moveToRecycleBin(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(
                isDeleted = true,
                deletedAt = LocalDateTime.now(),
                modifiedAt = LocalDateTime.now(),
                cloudId = note.cloudId.ifEmpty { UUID.randomUUID().toString() }
            )
            noteDao.update(updated)
            safeCloudCall(TAG, "recycle note") {
                firebaseManager.pushNote(updated)
            }
        }
    }

    fun restoreNote(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(
                isDeleted = false,
                deletedAt = null,
                modifiedAt = LocalDateTime.now(),
                cloudId = note.cloudId.ifEmpty { UUID.randomUUID().toString() }
            )
            noteDao.update(updated)
            safeCloudCall(TAG, "restore note") {
                firebaseManager.pushNote(updated)
            }
        }
    }

    fun deletePermanently(note: Note) {
        viewModelScope.launch {
            noteDao.deletePermanently(note)
            safeCloudCall(TAG, "hard-delete note") {
                firebaseManager.hardDeleteNote(note.cloudId)
            }
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
            // Capture cloudIds before the rows are gone
            val victims = noteDao.getDeletedNotesBefore(threshold)
            noteDao.deleteOldNotes(threshold)
            safeCloudCall(TAG, "clean up recycle bin") {
                victims.forEach { firebaseManager.hardDeleteNote(it.cloudId) }
            }
        }
    }
}
