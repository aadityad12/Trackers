package com.example.apextracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    @Query("SELECT * FROM budget_items WHERE date = :date")
    fun getItemsByDate(date: LocalDate): Flow<List<BudgetItem>>
}
