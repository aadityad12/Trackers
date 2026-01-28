package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val budgetDao = AppDatabase.getDatabase(application).budgetDao()
    val allItems: Flow<List<BudgetItem>> = budgetDao.getAllItems()

    fun addItem(title: String, amount: Double, description: String?, date: java.time.LocalDate) {
        viewModelScope.launch {
            budgetDao.insertItem(
                BudgetItem(
                    title = title,
                    amount = amount,
                    description = description,
                    date = date
                )
            )
        }
    }
}
