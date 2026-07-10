package com.example.apextracker

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
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

/**
 * Assigns a fresh cloudId to each reminder that needs one and resolves parentCloudId using
 * cloudIds assigned within this same batch (not just pre-existing ones), so a parent/child
 * recurring pair that are both syncing for the first time link correctly regardless of
 * iteration order.
 */
internal fun resolvePendingReminderCloudIds(
    remindersNeedingCloudId: List<Reminder>,
    existingCloudIdsById: Map<Long, String>,
    generateCloudId: () -> String = { UUID.randomUUID().toString() }
): List<Reminder> {
    val cloudIdById = existingCloudIdsById.toMutableMap()
    val assigned = remindersNeedingCloudId.map { reminder ->
        val newCloudId = generateCloudId()
        cloudIdById[reminder.id] = newCloudId
        reminder.copy(cloudId = newCloudId)
    }
    return assigned.map { reminder ->
        val parentCloudId = reminder.parentId?.let { pid -> cloudIdById[pid]?.takeIf { it.isNotEmpty() } }
        reminder.copy(parentCloudId = parentCloudId)
    }
}

/**
 * Runs a fire-and-forget cloud operation from a ViewModel. Room is always written
 * first and is the source of truth; a failed push (offline, signed out mid-flight)
 * must never crash or roll back the local mutation — it's logged and reconciled by
 * the next initial sync.
 */
internal suspend fun safeCloudCall(tag: String, op: String, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        Log.w(tag, "Cloud $op failed", e)
    }
}

// ── Cloud document parsing ────────────────────────────────────────────────────
// Pure functions so malformed-document handling is unit-testable. Each throws on a
// document that can't be represented locally; callers catch per-doc and log, so one
// bad document is skipped instead of aborting the whole sync. A blank cloudId is
// malformed by definition: legacy docs written by the old BudgetViewModel path
// serialized the entity default cloudId = "" and must not be re-imported.

private fun Map<String, Any?>.requireCloudId(): String =
    (this["cloudId"] as? String)?.takeIf { it.isNotBlank() }
        ?: error("missing or blank 'cloudId'")

private fun Map<String, Any?>.requireString(key: String): String =
    this[key] as? String ?: error("missing '$key'")

private fun Map<String, Any?>.optString(key: String): String? = this[key] as? String

private fun Map<String, Any?>.optLong(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L

private fun Map<String, Any?>.requireDouble(key: String): Double =
    (this[key] as? Number)?.toDouble() ?: error("missing or non-numeric '$key'")

internal data class ParsedBudgetItem(val item: BudgetItem, val categoryCloudId: String?)

internal fun parseCategoryDoc(data: Map<String, Any?>): Category = Category(
    name = data.requireString("name"),
    colorHex = data.requireString("colorHex"),
    cloudId = data.requireCloudId(),
    modifiedAt = data.optLong("modifiedAt")
)

internal fun parseBudgetItemDoc(data: Map<String, Any?>): ParsedBudgetItem = ParsedBudgetItem(
    item = BudgetItem(
        title = data.requireString("title"),
        amount = data.requireDouble("amount"),
        description = data.optString("description"),
        date = LocalDate.parse(data.requireString("date")),
        categoryId = null,
        cloudId = data.requireCloudId(),
        modifiedAt = data.optLong("modifiedAt")
    ),
    categoryCloudId = data.optString("categoryCloudId")
)

internal fun parseSubscriptionDoc(data: Map<String, Any?>): Subscription = Subscription(
    name = data.requireString("name"),
    amount = data.requireDouble("amount"),
    renewalDate = LocalDate.parse(data.requireString("renewalDate")),
    notes = data.optString("notes"),
    lastAddedDate = data.optString("lastAddedDate")?.let { LocalDate.parse(it) },
    cloudId = data.requireCloudId(),
    modifiedAt = data.optLong("modifiedAt")
)

internal fun parseNoteDoc(data: Map<String, Any?>): Note = Note(
    title = data.requireString("title"),
    content = data.requireString("content"),
    createdAt = LocalDateTime.parse(data.requireString("createdAt")),
    modifiedAt = LocalDateTime.parse(data.requireString("modifiedAt")),
    isDeleted = data["isDeleted"] as? Boolean ?: false,
    deletedAt = data.optString("deletedAt")?.let { LocalDateTime.parse(it) },
    cloudId = data.requireCloudId()
)

internal fun parseReminderDoc(data: Map<String, Any?>, gson: Gson): Reminder = Reminder(
    name = data.requireString("name"),
    date = LocalDate.parse(data.requireString("date")),
    time = data.optString("time")?.let { LocalTime.parse(it) },
    description = data.optString("description"),
    isCompleted = data["isCompleted"] as? Boolean ?: false,
    recurrence = data.optString("recurrence")?.let { gson.fromJson(it, Recurrence::class.java) },
    occurrencesCompleted = (data["occurrencesCompleted"] as? Number)?.toInt() ?: 0,
    cloudId = data.requireCloudId(),
    parentCloudId = data.optString("parentCloudId"),
    modifiedAt = data.optLong("modifiedAt")
)

internal fun parseStudySessionDoc(data: Map<String, Any?>): StudySession = StudySession(
    date = LocalDate.parse(data.requireString("date")),
    durationSeconds = (data["durationSeconds"] as? Number)?.toLong()
        ?: error("missing or non-numeric 'durationSeconds'")
)

class FirebaseManager(private val context: Context) {
    companion object {
        private const val TAG = "FirebaseManager"
    }

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

    private suspend fun pullAllBudgetItems(): List<Pair<String, Map<String, Any>>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("budget")
            .get().await()
            .documents.mapNotNull { d -> d.data?.let { d.id to it } }
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

    private suspend fun pullAllCategories(): List<Pair<String, Map<String, Any>>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("categories")
            .get().await()
            .documents.mapNotNull { d -> d.data?.let { d.id to it } }
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

    private suspend fun pullAllSubscriptions(): List<Pair<String, Map<String, Any>>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("subscriptions")
            .get().await()
            .documents.mapNotNull { d -> d.data?.let { d.id to it } }
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

    private suspend fun pullAllNotes(): List<Pair<String, Map<String, Any>>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("notes")
            .get().await()
            .documents.mapNotNull { d -> d.data?.let { d.id to it } }
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

    private suspend fun pullAllReminders(): List<Pair<String, Map<String, Any>>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("reminders")
            .get().await()
            .documents.mapNotNull { d -> d.data?.let { d.id to it } }
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

    private suspend fun pullAllStudySessions(): List<Pair<String, Map<String, Any>>> {
        val uid = userId ?: return emptyList()
        return firestore.collection("users").document(uid)
            .collection("study_sessions")
            .get().await()
            .documents.mapNotNull { d -> d.data?.let { d.id to it } }
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
        // Each step is isolated so one entity's failure (network, malformed data)
        // can't abort the others. Categories must run before budget items (FK lookup).
        syncStep("categories") { syncCategories(db) }
        syncStep("budget items") { syncBudgetItems(db) }
        syncStep("subscriptions") { syncSubscriptions(db) }
        syncStep("notes") { syncNotes(db) }
        syncStep("reminders") { syncReminders(db) }
        syncStep("study sessions") { syncStudySessions(db) }
        syncStep("excluded apps") { syncExcludedApps(db) }
    }

    private suspend fun syncStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Sync step '$name' failed", e)
        }
    }

    private suspend fun syncCategories(db: AppDatabase) {
        for ((docId, data) in pullAllCategories()) {
            try {
                val parsed = parseCategoryDoc(data)
                val local = db.categoryDao().getCategoryByCloudId(parsed.cloudId)
                if (local == null) {
                    db.categoryDao().insertCategory(parsed)
                } else if (parsed.modifiedAt > local.modifiedAt) {
                    db.categoryDao().updateCategory(
                        local.copy(name = parsed.name, colorHex = parsed.colorHex, modifiedAt = parsed.modifiedAt)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed category doc $docId", e)
            }
        }
        // Assign cloudIds where missing, then push ALL local rows — not just the
        // newly-assigned ones — so rows created or edited while signed out still reach
        // the cloud. Safe post-pull: the pull above already applied last-writer-wins.
        for (cat in db.categoryDao().getAllCategoriesOneShot()) {
            val toPush = if (cat.cloudId.isEmpty()) {
                val updated = cat.copy(cloudId = UUID.randomUUID().toString(), modifiedAt = System.currentTimeMillis())
                db.categoryDao().updateCategory(updated)
                updated
            } else cat
            pushCategory(toPush)
        }
    }

    private suspend fun syncBudgetItems(db: AppDatabase) {
        // Build a lookup map cloudId → local Room id for categories
        val catLookup = db.categoryDao().getAllCategoriesOneShot()
            .filter { it.cloudId.isNotEmpty() }
            .associate { it.cloudId to it.id }

        for ((docId, data) in pullAllBudgetItems()) {
            try {
                val (parsed, categoryCloudId) = parseBudgetItemDoc(data)
                val categoryId = categoryCloudId?.let { catLookup[it] }

                val local = db.budgetDao().getItemByCloudId(parsed.cloudId)
                if (local == null) {
                    db.budgetDao().insertItem(parsed.copy(categoryId = categoryId))
                } else if (parsed.modifiedAt > local.modifiedAt) {
                    db.budgetDao().updateItem(
                        local.copy(
                            title = parsed.title, amount = parsed.amount, description = parsed.description,
                            date = parsed.date, categoryId = categoryId, modifiedAt = parsed.modifiedAt
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed budget doc $docId", e)
            }
        }
        // Assign cloudIds where missing, then push ALL local rows (see syncCategories).
        // Re-fetch categories: syncCategories may have assigned cloudIds after catLookup was built.
        val allCats = db.categoryDao().getAllCategoriesOneShot()
        for (item in db.budgetDao().getAllItemsOneShot()) {
            val toPush = if (item.cloudId.isEmpty()) {
                val updated = item.copy(cloudId = UUID.randomUUID().toString(), modifiedAt = System.currentTimeMillis())
                db.budgetDao().updateItem(updated)
                updated
            } else item
            val catCloudId = toPush.categoryId?.let { cid ->
                allCats.find { it.id == cid }?.cloudId?.takeIf { it.isNotEmpty() }
            }
            pushBudgetItem(toPush, catCloudId)
        }
    }

    private suspend fun syncSubscriptions(db: AppDatabase) {
        for ((docId, data) in pullAllSubscriptions()) {
            try {
                val parsed = parseSubscriptionDoc(data)
                val local = db.subscriptionDao().getByCloudId(parsed.cloudId)
                if (local == null) {
                    db.subscriptionDao().insertSubscription(parsed)
                } else if (parsed.modifiedAt > local.modifiedAt) {
                    db.subscriptionDao().updateSubscription(
                        local.copy(
                            name = parsed.name, amount = parsed.amount, renewalDate = parsed.renewalDate,
                            notes = parsed.notes, lastAddedDate = parsed.lastAddedDate, modifiedAt = parsed.modifiedAt
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed subscription doc $docId", e)
            }
        }
        // Assign cloudIds where missing, then push ALL local rows (see syncCategories).
        for (sub in db.subscriptionDao().getAllSubscriptionsSync()) {
            val toPush = if (sub.cloudId.isEmpty()) {
                val updated = sub.copy(cloudId = UUID.randomUUID().toString(), modifiedAt = System.currentTimeMillis())
                db.subscriptionDao().updateSubscription(updated)
                updated
            } else sub
            pushSubscription(toPush)
        }
    }

    private suspend fun syncNotes(db: AppDatabase) {
        for ((docId, data) in pullAllNotes()) {
            try {
                val parsed = parseNoteDoc(data)
                val local = db.noteDao().getNoteByCloudId(parsed.cloudId)
                if (local == null) {
                    db.noteDao().insert(parsed)
                } else if (parsed.modifiedAt.isAfter(local.modifiedAt)) {
                    db.noteDao().update(
                        local.copy(
                            title = parsed.title, content = parsed.content, modifiedAt = parsed.modifiedAt,
                            isDeleted = parsed.isDeleted, deletedAt = parsed.deletedAt
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed note doc $docId", e)
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
        for ((docId, data) in cloudDocs) {
            try {
                val parsed = parseReminderDoc(data, gson)
                val local = db.reminderDao().getReminderByCloudId(parsed.cloudId)
                if (local == null) {
                    db.reminderDao().insertReminder(parsed)
                } else if (parsed.modifiedAt > local.modifiedAt) {
                    db.reminderDao().updateReminder(
                        local.copy(
                            name = parsed.name, date = parsed.date, time = parsed.time,
                            description = parsed.description, isCompleted = parsed.isCompleted,
                            recurrence = parsed.recurrence,
                            occurrencesCompleted = parsed.occurrencesCompleted,
                            parentCloudId = parsed.parentCloudId, modifiedAt = parsed.modifiedAt
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed reminder doc $docId", e)
            }
        }

        // Second pass: resolve parentCloudId → parentId
        for ((_, data) in cloudDocs) {
            val cloudId = (data["cloudId"] as? String)?.takeIf { it.isNotBlank() } ?: continue
            val parentCloudId = data["parentCloudId"] as? String ?: continue
            val child = db.reminderDao().getReminderByCloudId(cloudId) ?: continue
            val parent = db.reminderDao().getReminderByCloudId(parentCloudId) ?: continue
            if (child.parentId != parent.id) {
                db.reminderDao().updateReminder(child.copy(parentId = parent.id))
            }
        }

        // Push locally-created reminders with no cloudId
        val allLocalReminders = db.reminderDao().getAllRemindersOneShot()
        val existingCloudIdsById = allLocalReminders.associate { it.id to it.cloudId }
        val toPush = allLocalReminders.filter { it.cloudId.isEmpty() }
        for (reminder in resolvePendingReminderCloudIds(toPush, existingCloudIdsById)) {
            val updated = reminder.copy(modifiedAt = System.currentTimeMillis())
            db.reminderDao().updateReminder(updated)
            pushReminder(updated)
        }
    }

    private suspend fun syncStudySessions(db: AppDatabase) {
        for ((docId, data) in pullAllStudySessions()) {
            try {
                val parsed = parseStudySessionDoc(data)
                // Only insert if local doesn't have this date; local timer is source of truth
                if (db.studySessionDao().getSessionByDate(parsed.date) == null) {
                    db.studySessionDao().insertSession(parsed)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed study session doc $docId", e)
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
