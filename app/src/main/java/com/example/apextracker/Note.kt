package com.example.apextracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String, // Storing as JSON or structured string for lists
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val modifiedAt: LocalDateTime = LocalDateTime.now(),
    val isDeleted: Boolean = false,
    val deletedAt: LocalDateTime? = null,
    val cloudId: String = "",
    val isPinned: Boolean = false,
    /**
     * Image attachments (Issue #127) as a newline-separated list of filenames stored under the
     * app's private note-attachments dir. Device-local — deliberately NOT synced (the bytes can't
     * ride Firestore, and syncing bare filenames would show broken images on another device), so
     * it's absent from parseNoteDoc/pushNote and preserved across cloud updates by applyNoteDoc.
     */
    val attachments: String = ""
)
