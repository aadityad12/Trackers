package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val budgetDao = database.budgetDao()
    private val categoryDao = database.categoryDao()

    val allItems: Flow<List<BudgetItem>> = budgetDao.getAllItems()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    fun addItem(title: String, amount: Double, description: String?, date: LocalDate, categoryId: Long?) {
        viewModelScope.launch {
            budgetDao.insertItem(
                BudgetItem(
                    title = title,
                    amount = amount,
                    description = description,
                    date = date,
                    categoryId = categoryId
                )
            )
        }
    }

    fun deleteItem(item: BudgetItem) {
        viewModelScope.launch {
            budgetDao.deleteItem(item)
        }
    }

    fun addCategory(name: String, colorHex: String) {
        viewModelScope.launch {
            categoryDao.insertCategory(Category(name = name, colorHex = colorHex))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryDao.deleteCategory(category)
        }
    }
}
