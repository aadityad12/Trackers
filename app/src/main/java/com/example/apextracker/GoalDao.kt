package com.example.apextracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    /** Every goal including archived ones — history needs archived goals to score their active days. */
    @Query("SELECT * FROM goals ORDER BY sortOrder ASC, id ASC")
    fun getAllGoals(): Flow<List<Goal>>

    /** Only goals still active today, for the Today checklist and the goal-management list. */
    @Query("SELECT * FROM goals WHERE archivedDate IS NULL ORDER BY sortOrder ASC, id ASC")
    fun getActiveGoals(): Flow<List<Goal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Delete
    suspend fun deleteGoal(goal: Goal)

    @Query("SELECT * FROM goals WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getGoalByCloudId(cloudId: String): Goal?

    @Query("SELECT * FROM goals")
    suspend fun getAllGoalsOneShot(): List<Goal>
}
