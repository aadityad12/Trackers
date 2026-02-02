package com.example.apextracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions WHERE date = :date")
    suspend fun getSessionByDate(date: LocalDate): StudySession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession)

    @Update
    suspend fun updateSession(session: StudySession)

    @Query("SELECT * FROM study_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<StudySession>>
}
