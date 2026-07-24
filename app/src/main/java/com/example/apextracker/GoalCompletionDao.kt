package com.example.apextracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface GoalCompletionDao {
    @Query("SELECT * FROM goal_completions")
    fun getAllCompletions(): Flow<List<GoalCompletion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(completion: GoalCompletion)

    @Delete
    suspend fun delete(completion: GoalCompletion)

    @Query("SELECT * FROM goal_completions WHERE goalCloudId = :goalCloudId AND date = :date LIMIT 1")
    suspend fun getByGoalAndDate(goalCloudId: String, date: LocalDate): GoalCompletion?

    /** All completions for a goal — used to clean up when a goal is deleted. */
    @Query("SELECT * FROM goal_completions WHERE goalCloudId = :goalCloudId")
    suspend fun getByGoal(goalCloudId: String): List<GoalCompletion>

    @Query("SELECT * FROM goal_completions")
    suspend fun getAllCompletionsOneShot(): List<GoalCompletion>

    /** Wipes the table — used by the full-dataset restore (Issue #121). */
    @Query("DELETE FROM goal_completions")
    suspend fun clearAll()
}
