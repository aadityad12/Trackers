package com.example.apextracker

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class FirebaseManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val gson = Gson()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val userId: String? get() = auth.currentUser?.uid

    val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"
    }

    private val deviceName: String get() = Build.MODEL

    // ── Auth ─────────────────────────────────────────────────────────────────

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    suspend fun syncSettings(theme: String, isDarkMode: Boolean) {
        val uid = userId ?: return
        firestore.collection("users").document(uid)
            .set(
                mapOf(
                    "theme" to theme,
                    "isDarkMode" to isDarkMode,
                    "lastSynced" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
    }

    fun getSettingsFlow(): Flow<Map<String, Any>?> = callbackFlow {
        val uid = userId ?: return@callbackFlow
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ -> trySend(snapshot?.data) }
        awaitClose { listener.remove() }
    }

    // ── Budget Items ──────────────────────────────────────────────────────────

    suspend fun pushBudgetItem(item: BudgetItem, categoryCloudId: String?) {
        val uid = userId ?: return
        if (item.cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("budget").document(item.cloudId)
            .set(
                mapOf(
                    "cloudId" to item.cloudId,
                    "title" to item.title,
                    "amount" to item.amount,
                    "description" to item.description,
                    "date" to item.date.toString(),
                    "categoryCloudId" to categoryCloudId,
                    "modifiedAt" to item.modifiedAt
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun deleteBudgetItem(cloudId: String) {
        val uid = userId ?: return
        if (cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("budget").document(cloudId)
            .delete().await()
    }

    private suspend fun pullAllBudgetItems(): List<Map<String, Any>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("budget")
            .get().await()
            .documents.mapNotNull { it.data }
    }

    // ── Categories ────────────────────────────────────────────────────────────

    suspend fun pushCategory(category: Category) {
        val uid = userId ?: return
        if (category.cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("categories").document(category.cloudId)
            .set(
                mapOf(
                    "cloudId" to category.cloudId,
                    "name" to category.name,
                    "colorHex" to category.colorHex,
                    "modifiedAt" to category.modifiedAt
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun deleteCategory(cloudId: String) {
        val uid = userId ?: return
        if (cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("categories").document(cloudId)
            .delete().await()
    }

    private suspend fun pullAllCategories(): List<Map<String, Any>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("categories")
            .get().await()
            .documents.mapNotNull { it.data }
    }

    // ── Subscriptions ─────────────────────────────────────────────────────────

    suspend fun pushSubscription(subscription: Subscription) {
        val uid = userId ?: return
        if (subscription.cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("subscriptions").document(subscription.cloudId)
            .set(
                mapOf(
                    "cloudId" to subscription.cloudId,
                    "name" to subscription.name,
                    "amount" to subscription.amount,
                    "renewalDate" to subscription.renewalDate.toString(),
                    "notes" to subscription.notes,
                    "lastAddedDate" to subscription.lastAddedDate?.toString(),
                    "modifiedAt" to subscription.modifiedAt
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun deleteSubscription(cloudId: String) {
        val uid = userId ?: return
        if (cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("subscriptions").document(cloudId)
            .delete().await()
    }

    private suspend fun pullAllSubscriptions(): List<Map<String, Any>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("subscriptions")
            .get().await()
            .documents.mapNotNull { it.data }
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    suspend fun pushNote(note: Note) {
        val uid = userId ?: return
        if (note.cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("notes").document(note.cloudId)
            .set(
                mapOf(
                    "cloudId" to note.cloudId,
                    "title" to note.title,
                    "content" to note.content,
                    "createdAt" to note.createdAt.toString(),
                    "modifiedAt" to note.modifiedAt.toString(),
                    "isDeleted" to note.isDeleted,
                    "deletedAt" to note.deletedAt?.toString()
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun hardDeleteNote(cloudId: String) {
        val uid = userId ?: return
        if (cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("notes").document(cloudId)
            .delete().await()
    }

    private suspend fun pullAllNotes(): List<Map<String, Any>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("notes")
            .get().await()
            .documents.mapNotNull { it.data }
    }

    // ── Reminders ─────────────────────────────────────────────────────────────

    suspend fun pushReminder(reminder: Reminder) {
        val uid = userId ?: return
        if (reminder.cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("reminders").document(reminder.cloudId)
            .set(
                mapOf(
                    "cloudId" to reminder.cloudId,
                    "name" to reminder.name,
                    "date" to reminder.date.toString(),
                    "time" to reminder.time?.toString(),
                    "description" to reminder.description,
                    "isCompleted" to reminder.isCompleted,
                    "recurrence" to reminder.recurrence?.let { gson.toJson(it) },
                    "parentCloudId" to reminder.parentCloudId,
                    "occurrencesCompleted" to reminder.occurrencesCompleted,
                    "modifiedAt" to reminder.modifiedAt
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun deleteReminder(cloudId: String) {
        val uid = userId ?: return
        if (cloudId.isEmpty()) return
        firestore.collection("users").document(uid)
            .collection("reminders").document(cloudId)
            .delete().await()
    }

    private suspend fun pullAllReminders(): List<Map<String, Any>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("reminders")
            .get().await()
            .documents.mapNotNull { it.data }
    }

    // ── Study Sessions ────────────────────────────────────────────────────────

    suspend fun pushStudySession(session: StudySession) {
        val uid = userId ?: return
        val dateStr = session.date.toString()
        firestore.collection("users").document(uid)
            .collection("study_sessions").document(dateStr)
            .set(
                mapOf(
                    "date" to dateStr,
                    "durationSeconds" to session.durationSeconds
                ),
                SetOptions.merge()
            ).await()
    }

    private suspend fun pullAllStudySessions(): List<Map<String, Any>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("study_sessions")
            .get().await()
            .documents.mapNotNull { it.data }
    }

    // ── Screen Time ───────────────────────────────────────────────────────────

    suspend fun uploadScreenTimeSession(session: ScreenTimeSession) {
        val uid = userId ?: return
        val dateStr = session.date.toString()
        // Keep device info document up to date
        firestore.collection("users").document(uid)
            .collection("devices").document(deviceId)
            .set(
                mapOf("deviceName" to deviceName, "deviceId" to deviceId),
                SetOptions.merge()
            ).await()
        // Write daily session
        firestore.collection("users").document(uid)
            .collection("devices").document(deviceId)
            .collection("screen_time").document(dateStr)
            .set(
                mapOf(
                    "date" to dateStr,
                    "durationMillis" to session.durationMillis,
                    "deviceName" to deviceName,
                    "lastUpdated" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun getOtherDevicesTodayUsage(): List<DeviceSession> {
        val uid = userId ?: return emptyList()
        val today = LocalDate.now().toString()

        val devicesSnapshot = firestore.collection("users").document(uid)
            .collection("devices")
            .get().await()

        val result = mutableListOf<DeviceSession>()
        for (deviceDoc in devicesSnapshot.documents) {
            val dId = deviceDoc.id
            if (dId == deviceId) continue
            val dName = deviceDoc.getString("deviceName") ?: dId

            val sessionDoc = firestore.collection("users").document(uid)
                .collection("devices").document(dId)
                .collection("screen_time").document(today)
                .get().await()

            if (sessionDoc.exists()) {
                val millis = sessionDoc.getLong("durationMillis") ?: 0L
                result.add(
                    DeviceSession(
                        deviceId = dId,
                        deviceName = dName,
                        date = today,
                        durationMillis = millis,
                        isCurrentDevice = false
                    )
                )
            }
        }
        return result
    }

    // ── Excluded Apps ─────────────────────────────────────────────────────────

    suspend fun pushExcludedApp(packageName: String) {
        val uid = userId ?: return
        firestore.collection("users").document(uid)
            .collection("excluded_apps").document(packageName)
            .set(mapOf("packageName" to packageName))
            .await()
    }

    suspend fun removeExcludedApp(packageName: String) {
        val uid = userId ?: return
        firestore.collection("users").document(uid)
            .collection("excluded_apps").document(packageName)
            .delete().await()
    }

    private suspend fun pullAllExcludedApps(): List<String> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("excluded_apps")
            .get().await()
            .documents.mapNotNull { it.getString("packageName") }
    }

    // ── Initial Sync ──────────────────────────────────────────────────────────

    suspend fun performInitialSync(db: AppDatabase) {
        if (userId == null) return
        try {
            syncCategories(db)
            syncBudgetItems(db)
            syncSubscriptions(db)
            syncNotes(db)
            syncReminders(db)
            syncStudySessions(db)
            syncExcludedApps(db)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun syncCategories(db: AppDatabase) {
        val cloudDocs = pullAllCategories()
        // cloudId → local Room id, built as we sync, needed for budget item FK resolution
        for (doc in cloudDocs) {
            val cloudId = doc["cloudId"] as? String ?: continue
            val cloudModifiedAt = doc["modifiedAt"] as? Long ?: 0L
            val name = doc["name"] as? String ?: continue
            val colorHex = doc["colorHex"] as? String ?: continue

            val local = db.categoryDao().getCategoryByCloudId(cloudId)
            if (local == null) {
                db.categoryDao().insertCategory(
                    Category(name = name, colorHex = colorHex, cloudId = cloudId, modifiedAt = cloudModifiedAt)
                )
            } else if (cloudModifiedAt > local.modifiedAt) {
                db.categoryDao().updateCategory(
                    local.copy(name = name, colorHex = colorHex, modifiedAt = cloudModifiedAt)
                )
            }
        }
        // Push any locally-created categories that have no cloudId
        for (cat in db.categoryDao().getAllCategoriesOneShot().filter { it.cloudId.isEmpty() }) {
            val updated = cat.copy(cloudId = UUID.randomUUID().toString(), modifiedAt = System.currentTimeMillis())
            db.categoryDao().updateCategory(updated)
            pushCategory(updated)
        }
    }

    private suspend fun syncBudgetItems(db: AppDatabase) {
        // Build a lookup map cloudId → local Room id for categories
        val catLookup = db.categoryDao().getAllCategoriesOneShot()
            .filter { it.cloudId.isNotEmpty() }
            .associate { it.cloudId to it.id }

        for (doc in pullAllBudgetItems()) {
            val cloudId = doc["cloudId"] as? String ?: continue
            val cloudModifiedAt = doc["modifiedAt"] as? Long ?: 0L
            val title = doc["title"] as? String ?: continue
            val amount = (doc["amount"] as? Double) ?: (doc["amount"] as? Long)?.toDouble() ?: continue
            val description = doc["description"] as? String
            val dateStr = doc["date"] as? String ?: continue
            val categoryCloudId = doc["categoryCloudId"] as? String
            val date = LocalDate.parse(dateStr)
            val categoryId = categoryCloudId?.let { catLookup[it] }

            val local = db.budgetDao().getItemByCloudId(cloudId)
            if (local == null) {
                db.budgetDao().insertItem(
                    BudgetItem(
                        title = title, amount = amount, description = description,
                        date = date, categoryId = categoryId, cloudId = cloudId,
                        modifiedAt = cloudModifiedAt
                    )
                )
            } else if (cloudModifiedAt > local.modifiedAt) {
                db.budgetDao().updateItem(
                    local.copy(
                        title = title, amount = amount, description = description,
                        date = date, categoryId = categoryId, modifiedAt = cloudModifiedAt
                    )
                )
            }
        }
        // Push locally-created items with no cloudId
        val allCats = db.categoryDao().getAllCategoriesOneShot()
        for (item in db.budgetDao().getAllItemsOneShot().filter { it.cloudId.isEmpty() }) {
            val catCloudId = item.categoryId?.let { cid -> allCats.find { it.id == cid }?.cloudId }
            val updated = item.copy(cloudId = UUID.randomUUID().toString(), modifiedAt = System.currentTimeMillis())
            db.budgetDao().updateItem(updated)
            pushBudgetItem(updated, catCloudId)
        }
    }

    private suspend fun syncSubscriptions(db: AppDatabase) {
        for (doc in pullAllSubscriptions()) {
            val cloudId = doc["cloudId"] as? String ?: continue
            val cloudModifiedAt = doc["modifiedAt"] as? Long ?: 0L
            val name = doc["name"] as? String ?: continue
            val amount = (doc["amount"] as? Double) ?: (doc["amount"] as? Long)?.toDouble() ?: continue
            val renewalDateStr = doc["renewalDate"] as? String ?: continue
            val notes = doc["notes"] as? String
            val lastAddedDateStr = doc["lastAddedDate"] as? String
            val renewalDate = LocalDate.parse(renewalDateStr)
            val lastAddedDate = lastAddedDateStr?.let { LocalDate.parse(it) }

            val local = db.subscriptionDao().getByCloudId(cloudId)
            if (local == null) {
                db.subscriptionDao().insertSubscription(
                    Subscription(
                        name = name, amount = amount, renewalDate = renewalDate,
                        notes = notes, lastAddedDate = lastAddedDate, cloudId = cloudId,
                        modifiedAt = cloudModifiedAt
                    )
                )
            } else if (cloudModifiedAt > local.modifiedAt) {
                db.subscriptionDao().updateSubscription(
                    local.copy(
                        name = name, amount = amount, renewalDate = renewalDate,
                        notes = notes, lastAddedDate = lastAddedDate, modifiedAt = cloudModifiedAt
                    )
                )
            }
        }
        for (sub in db.subscriptionDao().getAllSubscriptionsSync().filter { it.cloudId.isEmpty() }) {
            val updated = sub.copy(cloudId = UUID.randomUUID().toString(), modifiedAt = System.currentTimeMillis())
            db.subscriptionDao().updateSubscription(updated)
            pushSubscription(updated)
        }
    }

    private suspend fun syncNotes(db: AppDatabase) {
        for (doc in pullAllNotes()) {
            val cloudId = doc["cloudId"] as? String ?: continue
            val cloudModifiedAtStr = doc["modifiedAt"] as? String ?: continue
            val cloudModifiedAt = LocalDateTime.parse(cloudModifiedAtStr)
            val title = doc["title"] as? String ?: continue
            val content = doc["content"] as? String ?: continue
            val createdAtStr = doc["createdAt"] as? String ?: continue
            val createdAt = LocalDateTime.parse(createdAtStr)
            val isDeleted = doc["isDeleted"] as? Boolean ?: false
            val deletedAt = (doc["deletedAt"] as? String)?.let { LocalDateTime.parse(it) }

            val local = db.noteDao().getNoteByCloudId(cloudId)
            if (local == null) {
                db.noteDao().insert(
                    Note(
                        title = title, content = content, createdAt = createdAt,
                        modifiedAt = cloudModifiedAt, isDeleted = isDeleted,
                        deletedAt = deletedAt, cloudId = cloudId
                    )
                )
            } else if (cloudModifiedAt.isAfter(local.modifiedAt)) {
                db.noteDao().update(
                    local.copy(
                        title = title, content = content, modifiedAt = cloudModifiedAt,
                        isDeleted = isDeleted, deletedAt = deletedAt
                    )
                )
            }
        }
        for (note in db.noteDao().getAllNotesOneShot().filter { it.cloudId.isEmpty() }) {
            val updated = note.copy(cloudId = UUID.randomUUID().toString())
            db.noteDao().update(updated)
            pushNote(updated)
        }
    }

    private suspend fun syncReminders(db: AppDatabase) {
        val cloudDocs = pullAllReminders()

        // First pass: insert/update all without resolving parentId
        for (doc in cloudDocs) {
            val cloudId = doc["cloudId"] as? String ?: continue
            val cloudModifiedAt = doc["modifiedAt"] as? Long ?: 0L
            val name = doc["name"] as? String ?: continue
            val dateStr = doc["date"] as? String ?: continue
            val time = (doc["time"] as? String)?.let { LocalTime.parse(it) }
            val description = doc["description"] as? String
            val isCompleted = doc["isCompleted"] as? Boolean ?: false
            val recurrence = (doc["recurrence"] as? String)?.let { gson.fromJson(it, Recurrence::class.java) }
            val occurrencesCompleted = (doc["occurrencesCompleted"] as? Long)?.toInt() ?: 0
            val parentCloudId = doc["parentCloudId"] as? String
            val date = LocalDate.parse(dateStr)

            val local = db.reminderDao().getReminderByCloudId(cloudId)
            if (local == null) {
                db.reminderDao().insertReminder(
                    Reminder(
                        name = name, date = date, time = time, description = description,
                        isCompleted = isCompleted, recurrence = recurrence,
                        occurrencesCompleted = occurrencesCompleted, cloudId = cloudId,
                        parentCloudId = parentCloudId, modifiedAt = cloudModifiedAt
                    )
                )
            } else if (cloudModifiedAt > local.modifiedAt) {
                db.reminderDao().updateReminder(
                    local.copy(
                        name = name, date = date, time = time, description = description,
                        isCompleted = isCompleted, recurrence = recurrence,
                        occurrencesCompleted = occurrencesCompleted,
                        parentCloudId = parentCloudId, modifiedAt = cloudModifiedAt
                    )
                )
            }
        }

        // Second pass: resolve parentCloudId → parentId
        for (doc in cloudDocs) {
            val cloudId = doc["cloudId"] as? String ?: continue
            val parentCloudId = doc["parentCloudId"] as? String ?: continue
            val child = db.reminderDao().getReminderByCloudId(cloudId) ?: continue
            val parent = db.reminderDao().getReminderByCloudId(parentCloudId) ?: continue
            if (child.parentId != parent.id) {
                db.reminderDao().updateReminder(child.copy(parentId = parent.id))
            }
        }

        // Push locally-created reminders with no cloudId
        val allLocalReminders = db.reminderDao().getAllRemindersOneShot()
        for (reminder in allLocalReminders.filter { it.cloudId.isEmpty() }) {
            val parentCloudId = reminder.parentId?.let { pid ->
                allLocalReminders.find { it.id == pid }?.cloudId?.takeIf { it.isNotEmpty() }
            }
            val updated = reminder.copy(
                cloudId = UUID.randomUUID().toString(),
                parentCloudId = parentCloudId,
                modifiedAt = System.currentTimeMillis()
            )
            db.reminderDao().updateReminder(updated)
            pushReminder(updated)
        }
    }

    private suspend fun syncStudySessions(db: AppDatabase) {
        for (doc in pullAllStudySessions()) {
            val dateStr = doc["date"] as? String ?: continue
            val durationSeconds = doc["durationSeconds"] as? Long ?: continue
            val date = LocalDate.parse(dateStr)
            // Only insert if local doesn't have this date; local timer is source of truth
            if (db.studySessionDao().getSessionByDate(date) == null) {
                db.studySessionDao().insertSession(StudySession(date = date, durationSeconds = durationSeconds))
            }
        }
        // Push all local sessions to cloud (upsert by date)
        for (session in db.studySessionDao().getAllSessionsOneShot()) {
            pushStudySession(session)
        }
    }

    private suspend fun syncExcludedApps(db: AppDatabase) {
        // Union: add all cloud apps to local
        for (packageName in pullAllExcludedApps()) {
            db.excludedAppDao().excludeApp(ExcludedApp(packageName = packageName))
        }
        // Push all local apps to cloud
        for (app in db.excludedAppDao().getAllExcludedAppsOneShot()) {
            pushExcludedApp(app.packageName)
        }
    }
}

data class DeviceSession(
    val deviceId: String,
    val deviceName: String,
    val date: String,
    val durationMillis: Long,
    val isCurrentDevice: Boolean
)
