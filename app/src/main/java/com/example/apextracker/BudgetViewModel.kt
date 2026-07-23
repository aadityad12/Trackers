package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "BudgetViewModel"
    }

    private val database = AppDatabase.getDatabase(application)
    private val budgetDao = database.budgetDao()
    private val categoryDao = database.categoryDao()
    private val subscriptionDao = database.subscriptionDao()

    private val firebaseManager = FirebaseManager(application)

    val allItems: Flow<List<BudgetItem>> = budgetDao.getAllItems()

    // Transaction-list search (Issue #123). Filtering happens in the view over the month's
    // already-loaded items, same as Notes/Reminders — see ListSearch.kt.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allSubscriptions: Flow<List<Subscription>> = subscriptionDao.getAllSubscriptions()

    // Guards checkAndAddSubscriptions so overlapping calls (init + every add/update) can't
    // race on the same subscription's renewalDate and double-insert a BudgetItem.
    private val subscriptionCatchUpMutex = Mutex()

    init {
        checkAndAddSubscriptions()
    }

    private suspend fun categoryCloudIdFor(categoryId: Long?): String? =
        categoryId?.let { categoryDao.getCategoryById(it)?.cloudId?.takeIf { c -> c.isNotEmpty() } }

    private fun checkAndAddSubscriptions() {
        viewModelScope.launch {
            subscriptionCatchUpMutex.withLock {
                val subscriptions = subscriptionDao.getAllSubscriptionsSync()
                val today = LocalDate.now()

                subscriptions.forEach { subscription ->
                    var currentRenewal = subscription.renewalDate
                    var updatedSub = subscription
                    val subscriptionCategoryId = -1L

                    while (currentRenewal.isBefore(today) || currentRenewal.isEqual(today)) {
                        val item = BudgetItem(
                            // Just the name: the "[Subscription]" label is composed at render
                            // time from strings.xml so it localizes (Issue #119).
                            title = updatedSub.name,
                            amount = updatedSub.amount,
                            description = updatedSub.notes,
                            date = currentRenewal,
                            categoryId = subscriptionCategoryId,
                            cloudId = UUID.randomUUID().toString(),
                            modifiedAt = System.currentTimeMillis()
                        )
                        budgetDao.insertItem(item)
                        safeCloudCall(TAG, "push subscription budget item") {
                            firebaseManager.pushBudgetItem(item, null)
                        }

                        currentRenewal = currentRenewal.plusMonths(1)
                        updatedSub = updatedSub.copy(
                            renewalDate = currentRenewal,
                            lastAddedDate = today
                        )
                        // Direct DAO call (not the public updateSubscription) so this catch-up
                        // pass doesn't re-trigger another concurrent checkAndAddSubscriptions().
                        subscriptionDao.updateSubscription(updatedSub)
                    }

                    if (updatedSub != subscription) {
                        val finalSub = updatedSub.copy(
                            cloudId = updatedSub.cloudId.ifEmpty { UUID.randomUUID().toString() },
                            modifiedAt = System.currentTimeMillis()
                        )
                        subscriptionDao.updateSubscription(finalSub)
                        safeCloudCall(TAG, "push subscription renewal advance") {
                            firebaseManager.pushSubscription(finalSub)
                        }
                    }
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
                categoryId = categoryId,
                cloudId = UUID.randomUUID().toString(),
                modifiedAt = System.currentTimeMillis()
            )
            budgetDao.insertItem(item)
            safeCloudCall(TAG, "push budget item") {
                firebaseManager.pushBudgetItem(item, categoryCloudIdFor(item.categoryId))
            }
        }
    }

    fun updateItem(item: BudgetItem) {
        viewModelScope.launch {
            val updated = item.copy(
                cloudId = item.cloudId.ifEmpty { UUID.randomUUID().toString() },
                modifiedAt = System.currentTimeMillis()
            )
            budgetDao.updateItem(updated)
            safeCloudCall(TAG, "update budget item") {
                firebaseManager.pushBudgetItem(updated, categoryCloudIdFor(updated.categoryId))
            }
        }
    }

    fun deleteItem(item: BudgetItem) {
        viewModelScope.launch {
            budgetDao.deleteItem(item)
            safeCloudCall(TAG, "delete budget item") {
                firebaseManager.deleteBudgetItem(item.cloudId)
            }
        }
    }

    /** Undo for [deleteItem]: re-inserts the preserved item unchanged (REPLACE keeps its
     *  Room id). The cloud delete has already been pushed by then; re-pushing the same
     *  cloudId recreates the doc. */
    fun restoreItem(item: BudgetItem) {
        viewModelScope.launch {
            budgetDao.insertItem(item)
            safeCloudCall(TAG, "restore budget item") {
                firebaseManager.pushBudgetItem(item, categoryCloudIdFor(item.categoryId))
            }
        }
    }

    fun addCategory(name: String, colorHex: String, monthlyLimit: Double?) {
        viewModelScope.launch {
            val category = Category(
                name = name,
                colorHex = colorHex,
                monthlyLimit = monthlyLimit,
                cloudId = UUID.randomUUID().toString(),
                modifiedAt = System.currentTimeMillis()
            )
            categoryDao.insertCategory(category)
            safeCloudCall(TAG, "push category") {
                firebaseManager.pushCategory(category)
            }
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            val updated = category.copy(
                cloudId = category.cloudId.ifEmpty { UUID.randomUUID().toString() },
                modifiedAt = System.currentTimeMillis()
            )
            categoryDao.updateCategory(updated)
            safeCloudCall(TAG, "update category") {
                firebaseManager.pushCategory(updated)
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            // Detach referencing items first: a deleted category would otherwise leave their
            // categoryId dangling, silently dropping those expenses out of per-category
            // groupings. Nulling it makes them explicitly uncategorized, locally and in cloud.
            val detached = budgetDao.getItemsByCategory(category.id).map {
                it.copy(
                    categoryId = null,
                    cloudId = it.cloudId.ifEmpty { UUID.randomUUID().toString() },
                    modifiedAt = System.currentTimeMillis()
                )
            }
            detached.forEach { budgetDao.updateItem(it) }
            categoryDao.deleteCategory(category)
            safeCloudCall(TAG, "delete category") {
                detached.forEach { firebaseManager.pushBudgetItem(it, categoryCloudId = null) }
                firebaseManager.deleteCategory(category.cloudId)
            }
        }
    }

    fun addSubscription(name: String, amount: Double, renewalDate: LocalDate, notes: String?) {
        viewModelScope.launch {
            val subscription = Subscription(
                name = name,
                amount = amount,
                renewalDate = renewalDate,
                notes = notes,
                cloudId = UUID.randomUUID().toString(),
                modifiedAt = System.currentTimeMillis()
            )
            subscriptionDao.insertSubscription(subscription)
            safeCloudCall(TAG, "push subscription") {
                firebaseManager.pushSubscription(subscription)
            }
            checkAndAddSubscriptions()
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            val updated = subscription.copy(
                cloudId = subscription.cloudId.ifEmpty { UUID.randomUUID().toString() },
                modifiedAt = System.currentTimeMillis()
            )
            subscriptionDao.updateSubscription(updated)
            safeCloudCall(TAG, "update subscription") {
                firebaseManager.pushSubscription(updated)
            }
            checkAndAddSubscriptions()
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            subscriptionDao.deleteSubscription(subscription)
            safeCloudCall(TAG, "delete subscription") {
                firebaseManager.deleteSubscription(subscription.cloudId)
            }
        }
    }
}
