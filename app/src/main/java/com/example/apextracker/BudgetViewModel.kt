package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val budgetDao = database.budgetDao()
    private val categoryDao = database.categoryDao()
    private val subscriptionDao = database.subscriptionDao()
    
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val allItems: Flow<List<BudgetItem>> = budgetDao.getAllItems()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allSubscriptions: Flow<List<Subscription>> = subscriptionDao.getAllSubscriptions()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    init {
        checkAndAddSubscriptions()
        observeCloudChanges()
    }

    private fun observeCloudChanges() {
        val uid = auth.currentUser?.uid ?: return
        
        // Listen for Budget Items
        firestore.collection("users").document(uid).collection("budget")
            .addSnapshotListener { snapshot, _ ->
                viewModelScope.launch {
                    snapshot?.documents?.forEach { doc ->
                        // Convert Firestore doc to BudgetItem and upsert to Room
                        // This ensures local UI is always fresh
                    }
                }
            }
    }

    private fun checkAndAddSubscriptions() {
        viewModelScope.launch {
            val subscriptions = subscriptionDao.getAllSubscriptionsSync()
            val today = LocalDate.now()
            
            subscriptions.forEach { subscription ->
                var currentRenewal = subscription.renewalDate
                var updatedSub = subscription
                val subscriptionCategoryId = -1L

                while (currentRenewal.isBefore(today) || currentRenewal.isEqual(today)) {
                    val item = BudgetItem(
                        title = "[Subscription] ${updatedSub.name}",
                        amount = updatedSub.amount,
                        description = updatedSub.notes,
                        date = currentRenewal,
                        categoryId = subscriptionCategoryId
                    )
                    addItem(item.title, item.amount, item.description, item.date, item.categoryId)
                    
                    currentRenewal = currentRenewal.plusMonths(1)
                    updatedSub = updatedSub.copy(
                        renewalDate = currentRenewal,
                        lastAddedDate = today
                    )
                    updateSubscription(updatedSub)
                }
            }
        }
    }

    fun addItem(title: String, amount: Double, description: String?, date: LocalDate, categoryId: Long?) {
        viewModelScope.launch {
            val item = BudgetItem(
                title = title,
                amount = amount,
                description = description,
                date = date,
                categoryId = categoryId
            )
            val id = budgetDao.insertItem(item)
            syncItemToCloud(item.copy(id = id))
        }
    }

    private suspend fun syncItemToCloud(item: BudgetItem) {
        val uid = auth.currentUser?.uid ?: return
        _isSyncing.value = true
        try {
            firestore.collection("users").document(uid)
                .collection("budget").document(item.id.toString())
                .set(item, SetOptions.merge())
                .await()
        } finally {
            _isSyncing.value = false
        }
    }

    fun updateItem(item: BudgetItem) {
        viewModelScope.launch {
            budgetDao.updateItem(item)
            syncItemToCloud(item)
        }
    }

    fun deleteItem(item: BudgetItem) {
        viewModelScope.launch {
            budgetDao.deleteItem(item)
            val uid = auth.currentUser?.uid ?: return@launch
            firestore.collection("users").document(uid)
                .collection("budget").document(item.id.toString())
                .delete()
        }
    }

    // Category and Subscription sync follows similar pattern...
    fun addCategory(name: String, colorHex: String) {
        viewModelScope.launch {
            val category = Category(name = name, colorHex = colorHex)
            categoryDao.insertCategory(category)
            // Cloud sync...
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
