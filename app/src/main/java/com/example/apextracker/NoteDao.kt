package com.example.apextracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY modifiedAt DESC")
    fun getAllActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedNotes(): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun deletePermanently(note: Note)

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND deletedAt < :threshold")
    suspend fun deleteOldNotes(threshold: LocalDateTime)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getNoteByCloudId(cloudId: String): Note?

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesOneShot(): List<Note>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 AND deletedAt < :threshold")
    suspend fun getDeletedNotesBefore(threshold: LocalDateTime): List<Note>
}
