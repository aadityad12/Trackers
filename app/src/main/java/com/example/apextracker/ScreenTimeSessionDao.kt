package com.example.apextracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ScreenTimeSessionDao {
    @Query("SELECT * FROM screen_time_sessions WHERE date = :date")
    suspend fun getSessionByDate(date: LocalDate): ScreenTimeSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScreenTimeSession)

    @Query("SELECT * FROM screen_time_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<ScreenTimeSession>>
}
