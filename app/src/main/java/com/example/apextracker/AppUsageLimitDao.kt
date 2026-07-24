package com.example.apextracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageLimitDao {
    @Query("SELECT * FROM app_usage_limits")
    fun getLimits(): Flow<List<AppUsageLimit>>

    @Query("SELECT * FROM app_usage_limits")
    suspend fun getLimitsOneShot(): List<AppUsageLimit>

    @Query("SELECT * FROM app_usage_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getLimit(packageName: String): AppUsageLimit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLimit(limit: AppUsageLimit)

    @Query("DELETE FROM app_usage_limits WHERE packageName = :packageName")
    suspend fun clearLimit(packageName: String)

    @Query("UPDATE app_usage_limits SET lastNotifiedDate = :date WHERE packageName = :packageName")
    suspend fun markNotified(packageName: String, date: String)
}
