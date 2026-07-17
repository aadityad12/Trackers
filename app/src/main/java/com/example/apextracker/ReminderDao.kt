package com.example.apextracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY date ASC, COALESCE(time, '00:00:00') ASC")
    fun getActiveReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 1 ORDER BY date ASC, COALESCE(time, '00:00:00') ASC")
    fun getCompletedReminders(): Flow<List<Reminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    /**
     * Flips an active reminder to completed, returning the number of rows changed — 0 means it
     * was already completed. The `isCompleted = 0` predicate makes this a compare-and-set in a
     * single statement, so of two concurrent completions only one can win (see [completeReminder]).
     */
    @Query("UPDATE reminders SET isCompleted = 1, cloudId = :cloudId, modifiedAt = :modifiedAt WHERE id = :id AND isCompleted = 0")
    suspend fun markCompletedIfActive(id: Long, cloudId: String, modifiedAt: Long): Int

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE isCompleted = 1")
    suspend fun clearAllCompleted()

    @Query("DELETE FROM reminders WHERE id IN (:ids)")
    suspend fun deleteRemindersByIds(ids: List<Long>)

    @Query("SELECT * FROM reminders WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getReminderByCloudId(cloudId: String): Reminder?

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): Reminder?

    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersOneShot(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE isCompleted = 1")
    suspend fun getCompletedRemindersOneShot(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id IN (:ids)")
    suspend fun getRemindersByIds(ids: List<Long>): List<Reminder>
}
