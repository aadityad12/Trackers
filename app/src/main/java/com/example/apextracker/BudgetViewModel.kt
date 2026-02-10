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
    private val subscriptionDao = database.subscriptionDao()

    val allItems: Flow<List<BudgetItem>> = budgetDao.getAllItems()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allSubscriptions: Flow<List<Subscription>> = subscriptionDao.getAllSubscriptions()

    init {
        checkAndAddSubscriptions()
    }

    private fun checkAndAddSubscriptions() {
        viewModelScope.launch {
            val subscriptions = subscriptionDao.getAllSubscriptionsSync()
            val today = LocalDate.now()
            
            subscriptions.forEach { subscription ->
                var currentRenewal = subscription.renewalDate
                var updatedSub = subscription
                
                // Special ID for Subscriptions
                val subscriptionCategoryId = -1L

                // Catch up on any missed renewals
                while (currentRenewal.isBefore(today) || currentRenewal.isEqual(today)) {
                    // Add the expense to the tracker using the actual renewal date
                    budgetDao.insertItem(
                        BudgetItem(
                            title = "[Subscription] ${updatedSub.name}",
                            amount = updatedSub.amount,
                            description = updatedSub.notes,
                            date = currentRenewal,
                            categoryId = subscriptionCategoryId
                        )
                    )
                    
                    // Move to next month
                    currentRenewal = currentRenewal.plusMonths(1)
                    updatedSub = updatedSub.copy(
                        renewalDate = currentRenewal,
                        lastAddedDate = today
                    )
                    subscriptionDao.updateSubscription(updatedSub)
                }
            }
        }
    }

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

    fun updateItem(item: BudgetItem) {
        viewModelScope.launch {
            budgetDao.updateItem(item)
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

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            categoryDao.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryDao.deleteCategory(category)
        }
    }

    // Subscription actions
    fun addSubscription(name: String, amount: Double, renewalDate: LocalDate, notes: String?) {
        viewModelScope.launch {
            subscriptionDao.insertSubscription(
                Subscription(name = name, amount = amount, renewalDate = renewalDate, notes = notes)
            )
            checkAndAddSubscriptions()
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            subscriptionDao.updateSubscription(subscription)
            checkAndAddSubscriptions()
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            subscriptionDao.deleteSubscription(subscription)
        }
    }
}
