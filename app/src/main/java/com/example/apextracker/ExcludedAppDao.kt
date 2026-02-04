package com.example.apextracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedAppDao {
    @Query("SELECT * FROM excluded_apps")
    fun getExcludedApps(): Flow<List<ExcludedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun excludeApp(app: ExcludedApp)

    @Delete
    suspend fun includeApp(app: ExcludedApp)
}
