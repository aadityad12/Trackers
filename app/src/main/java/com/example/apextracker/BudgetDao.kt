package com.example.apextracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget_items ORDER BY date DESC, id DESC")
    fun getAllItems(): Flow<List<BudgetItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: BudgetItem): Long

    @Update
    suspend fun updateItem(item: BudgetItem)

    @Delete
    suspend fun deleteItem(item: BudgetItem)

    @Query("SELECT * FROM budget_items WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getItemByCloudId(cloudId: String): BudgetItem?

    @Query("SELECT * FROM budget_items WHERE categoryId = :categoryId")
    suspend fun getItemsByCategory(categoryId: Long): List<BudgetItem>

    @Query("SELECT * FROM budget_items")
    suspend fun getAllItemsOneShot(): List<BudgetItem>

    /** Wipes the table — used by the full-dataset restore (Issue #121). */
    @Query("DELETE FROM budget_items")
    suspend fun clearAll()
}
