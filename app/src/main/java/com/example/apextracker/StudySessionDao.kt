package com.example.apextracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions WHERE date = :date AND subject = :subject")
    suspend fun getSession(date: LocalDate, subject: String): StudySession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession)

    @Query("SELECT * FROM study_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions")
    suspend fun getAllSessionsOneShot(): List<StudySession>

    /** Wipes the table — used by the full-dataset restore (Issue #121). */
    @Query("DELETE FROM study_sessions")
    suspend fun clearAll()
}
