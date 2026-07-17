package com.example.apextracker

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
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

/** Absent, null, or non-numeric all mean "not set" — used for optional caps. */
private fun Map<String, Any?>.optDouble(key: String): Double? = (this[key] as? Number)?.toDouble()

internal data class ParsedBudgetItem(val item: BudgetItem, val categoryCloudId: String?)

// ── Legacy budget doc migration ───────────────────────────────────────────────
// The old BudgetViewModel sync path wrote users/{uid}/budget/{roomId} docs via raw
// POJO serialization: cloudId = "", modifiedAt = 0, date as a nested object, doc key
// = the local Room autoincrement id. These are unreadable by the cloudId scheme and
// used to resurrect as duplicates. During sync we migrate what's salvageable and
// delete the legacy doc.

internal sealed interface LegacyBudgetDocAction {
    /** Salvageable and not present locally: insert locally under a fresh cloudId, push, delete legacy doc. */
    data class Migrate(val item: BudgetItem) : LegacyBudgetDocAction
    /** Duplicate of an existing local item, or missing required fields: just delete the legacy doc. */
    data object DeleteOnly : LegacyBudgetDocAction
}

private fun parseLegacyDate(raw: Any?): LocalDate? = when (raw) {
    is String -> try { LocalDate.parse(raw) } catch (e: Exception) { null }
    // Raw POJO serialization of LocalDate produces a nested map of its getters
    is Map<*, *> -> {
        val year = (raw["year"] as? Number)?.toInt()
        val month = (raw["monthValue"] as? Number)?.toInt()
        val day = (raw["dayOfMonth"] as? Number)?.toInt()
        if (year != null && month != null && day != null) {
            try { LocalDate.of(year, month, day) } catch (e: Exception) { null }
        } else null
    }
    else -> null
}

/**
 * Decides what to do with a pulled budget doc that has a null/blank cloudId field.
 * Returns null if the doc is NOT legacy (has a proper cloudId).
 *
 * Dedup guard: the common case is that the device which wrote the legacy doc still
 * has the Room row, which the unified path re-pushes under a UUID — migrating would
 * duplicate it. A local item with the same title and amount (and date, when the
 * legacy date is readable) counts as that duplicate.
 *
 * The migrated item's categoryId is dropped: the legacy doc stored a local Room id
 * from whichever install wrote it, which is meaningless on this device.
 */
internal fun classifyLegacyBudgetDoc(
    data: Map<String, Any?>,
    localItems: List<BudgetItem>,
    fallbackDate: LocalDate
): LegacyBudgetDocAction? {
    if (!(data["cloudId"] as? String).isNullOrBlank()) return null // not legacy

    val title = data["title"] as? String ?: return LegacyBudgetDocAction.DeleteOnly
    val amount = (data["amount"] as? Number)?.toDouble() ?: return LegacyBudgetDocAction.DeleteOnly
    val date = parseLegacyDate(data["date"])

    val duplicatesLocal = localItems.any {
        it.title == title && it.amount == amount && (date == null || it.date == date)
    }
    if (duplicatesLocal) return LegacyBudgetDocAction.DeleteOnly

    return LegacyBudgetDocAction.Migrate(
        BudgetItem(
            title = title,
            amount = amount,
            description = data["description"] as? String,
            date = date ?: fallbackDate,
            categoryId = null
        )
    )
}

internal fun parseCategoryDoc(data: Map<String, Any?>): Category = Category(
    name = data.requireString("name"),
    colorHex = data.requireString("colorHex"),
    // Optional, not required: docs written before per-category limits existed have no
    // such field, and a cleared cap is pushed as an explicit null. Both mean uncapped.
    monthlyLimit = data.optDouble("monthlyLimit"),
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
    cloudId = data.requireCloudId(),
    isPinned = data["isPinned"] as? Boolean ?: false
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
        // ANDROID_ID can (rarely) be null/blank; a shared literal fallback would make all such
        // devices on one account write to the same devices/{id} doc, overwriting each other's
        // screen time. Generate a per-install UUID instead and persist it so it stays stable.
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: persistedFallbackDeviceId()
    }

    private fun persistedFallbackDeviceId(): String {
        val prefs = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
        prefs.getString("fallback_device_id", null)?.let { return it }
        val generated = "device_" + UUID.randomUUID().toString()
        prefs.edit().putString("fallback_device_id", generated).apply()
        return generated
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
            .addSnapshotListener { snapshot, _ ->
                // Firestore fires listeners for this device's own writes too (echo). Skipping
                // snapshots with pending local writes means only server-acknowledged remote
                // state reaches the theme/dark-mode UI, so a local change can't bounce back
                // and transiently revert a rapid follow-up change.
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                trySend(snapshot?.data)
            }
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
                    // Always present in the map, so a cleared cap writes an explicit null
                    // instead of merge() leaving the old value behind.
                    "monthlyLimit" to category.monthlyLimit,
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
                    "deletedAt" to note.deletedAt?.toString(),
                    "isPinned" to note.isPinned
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

    private suspend fun applyCategoryDoc(db: AppDatabase, docId: String, data: Map<String, Any?>) {
        try {
            val parsed = parseCategoryDoc(data)
            val local = db.categoryDao().getCategoryByCloudId(parsed.cloudId)
            if (local == null) {
                db.categoryDao().insertCategory(parsed)
            } else if (parsed.modifiedAt > local.modifiedAt) {
                db.categoryDao().updateCategory(
                    local.copy(
                        name = parsed.name,
                        colorHex = parsed.colorHex,
                        monthlyLimit = parsed.monthlyLimit,
                        modifiedAt = parsed.modifiedAt
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Skipping malformed category doc $docId", e)
        }
    }

    private suspend fun removeCategoryByCloudId(db: AppDatabase, cloudId: String) {
        val local = db.categoryDao().getCategoryByCloudId(cloudId)
        if (local == null || local.cloudId.isEmpty()) return
        db.categoryDao().deleteCategory(local)
    }

    private fun categoryChangesFlow(): Flow<List<DocumentChange>> = callbackFlow {
        val uid = userId ?: return@callbackFlow awaitClose { }
        val listener = firestore.collection("users").document(uid)
            .collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "Category listener error", error); return@addSnapshotListener }
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                trySend(snapshot.documentChanges)
            }
        awaitClose { listener.remove() }
    }

    suspend fun collectCategoryChanges(db: AppDatabase) {
        categoryChangesFlow().collect { changes ->
            for (change in changes) {
                val docId = change.document.id
                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> applyCategoryDoc(db, docId, change.document.data)
                    DocumentChange.Type.REMOVED -> removeCategoryByCloudId(db, docId)
                }
            }
        }
    }

    private suspend fun syncCategories(db: AppDatabase) {
        for ((docId, data) in pullAllCategories()) {
            applyCategoryDoc(db, docId, data)
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

    private suspend fun applyBudgetItemDoc(db: AppDatabase, docId: String, data: Map<String, Any?>, catLookup: Map<String, Long>) {
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

    private suspend fun removeBudgetItemByCloudId(db: AppDatabase, cloudId: String) {
        val local = db.budgetDao().getItemByCloudId(cloudId)
        if (local == null || local.cloudId.isEmpty()) return
        db.budgetDao().deleteItem(local)
    }

    private fun budgetItemChangesFlow(): Flow<List<DocumentChange>> = callbackFlow {
        val uid = userId ?: return@callbackFlow awaitClose { }
        val listener = firestore.collection("users").document(uid)
            .collection("budget")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "Budget item listener error", error); return@addSnapshotListener }
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                trySend(snapshot.documentChanges)
            }
        awaitClose { listener.remove() }
    }

    suspend fun collectBudgetItemChanges(db: AppDatabase) {
        budgetItemChangesFlow().collect { changes ->
            // Recomputed each fire: a referenced category may have arrived via its own
            // listener at any time, independent of this snapshot.
            val catLookup = db.categoryDao().getAllCategoriesOneShot()
                .filter { it.cloudId.isNotEmpty() }
                .associate { it.cloudId to it.id }
            for (change in changes) {
                val docId = change.document.id
                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> applyBudgetItemDoc(db, docId, change.document.data, catLookup)
                    DocumentChange.Type.REMOVED -> removeBudgetItemByCloudId(db, docId)
                }
            }
        }
    }

    private suspend fun syncBudgetItems(db: AppDatabase) {
        // Build a lookup map cloudId → local Room id for categories
        val catLookup = db.categoryDao().getAllCategoriesOneShot()
            .filter { it.cloudId.isNotEmpty() }
            .associate { it.cloudId to it.id }

        val pulledDocs = pullAllBudgetItems()

        // Migrate/remove legacy docs (blank cloudId, written by the old ad-hoc path)
        // before the regular pull so they aren't logged as malformed every sync.
        val (legacyDocs, modernDocs) = pulledDocs.partition { (_, data) ->
            (data["cloudId"] as? String).isNullOrBlank()
        }
        if (legacyDocs.isNotEmpty()) {
            val localItems = db.budgetDao().getAllItemsOneShot()
            for ((docId, data) in legacyDocs) {
                try {
                    when (val action = classifyLegacyBudgetDoc(data, localItems, LocalDate.now())) {
                        is LegacyBudgetDocAction.Migrate -> {
                            val item = action.item.copy(
                                cloudId = UUID.randomUUID().toString(),
                                modifiedAt = System.currentTimeMillis()
                            )
                            db.budgetDao().insertItem(item)
                            pushBudgetItem(item, null)
                            deleteBudgetItem(docId)
                            Log.i(TAG, "Migrated legacy budget doc $docId to cloudId ${item.cloudId}")
                        }
                        LegacyBudgetDocAction.DeleteOnly -> {
                            deleteBudgetItem(docId)
                            Log.i(TAG, "Deleted legacy budget doc $docId (duplicate or unsalvageable)")
                        }
                        null -> {} // unreachable: partition guarantees blank cloudId
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to migrate legacy budget doc $docId", e)
                }
            }
        }

        for ((docId, data) in modernDocs) {
            applyBudgetItemDoc(db, docId, data, catLookup)
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

    private suspend fun applySubscriptionDoc(db: AppDatabase, docId: String, data: Map<String, Any?>) {
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

    private suspend fun removeSubscriptionByCloudId(db: AppDatabase, cloudId: String) {
        val local = db.subscriptionDao().getByCloudId(cloudId)
        if (local == null || local.cloudId.isEmpty()) return
        db.subscriptionDao().deleteSubscription(local)
    }

    private fun subscriptionChangesFlow(): Flow<List<DocumentChange>> = callbackFlow {
        val uid = userId ?: return@callbackFlow awaitClose { }
        val listener = firestore.collection("users").document(uid)
            .collection("subscriptions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "Subscription listener error", error); return@addSnapshotListener }
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                trySend(snapshot.documentChanges)
            }
        awaitClose { listener.remove() }
    }

    suspend fun collectSubscriptionChanges(db: AppDatabase) {
        subscriptionChangesFlow().collect { changes ->
            for (change in changes) {
                val docId = change.document.id
                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> applySubscriptionDoc(db, docId, change.document.data)
                    DocumentChange.Type.REMOVED -> removeSubscriptionByCloudId(db, docId)
                }
            }
        }
    }

    private suspend fun syncSubscriptions(db: AppDatabase) {
        for ((docId, data) in pullAllSubscriptions()) {
            applySubscriptionDoc(db, docId, data)
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

    private suspend fun applyNoteDoc(db: AppDatabase, docId: String, data: Map<String, Any?>) {
        try {
            val parsed = parseNoteDoc(data)
            val local = db.noteDao().getNoteByCloudId(parsed.cloudId)
            if (local == null) {
                db.noteDao().insert(parsed)
            } else if (parsed.modifiedAt.isAfter(local.modifiedAt)) {
                db.noteDao().update(
                    local.copy(
                        title = parsed.title, content = parsed.content, modifiedAt = parsed.modifiedAt,
                        isDeleted = parsed.isDeleted, deletedAt = parsed.deletedAt, isPinned = parsed.isPinned
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Skipping malformed note doc $docId", e)
        }
    }

    private suspend fun removeNoteByCloudId(db: AppDatabase, cloudId: String) {
        val local = db.noteDao().getNoteByCloudId(cloudId)
        if (local == null || local.cloudId.isEmpty()) return
        db.noteDao().deletePermanently(local)
    }

    private fun noteChangesFlow(): Flow<List<DocumentChange>> = callbackFlow {
        val uid = userId ?: return@callbackFlow awaitClose { }
        val listener = firestore.collection("users").document(uid)
            .collection("notes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "Note listener error", error); return@addSnapshotListener }
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                trySend(snapshot.documentChanges)
            }
        awaitClose { listener.remove() }
    }

    suspend fun collectNoteChanges(db: AppDatabase) {
        noteChangesFlow().collect { changes ->
            for (change in changes) {
                val docId = change.document.id
                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> applyNoteDoc(db, docId, change.document.data)
                    DocumentChange.Type.REMOVED -> removeNoteByCloudId(db, docId)
                }
            }
        }
    }

    private suspend fun syncNotes(db: AppDatabase) {
        for ((docId, data) in pullAllNotes()) {
            applyNoteDoc(db, docId, data)
        }
        // Assign cloudIds where missing, then push ALL local rows (see syncCategories).
        for (note in db.noteDao().getAllNotesOneShot()) {
            val toPush = if (note.cloudId.isEmpty()) {
                val updated = note.copy(cloudId = UUID.randomUUID().toString())
                db.noteDao().update(updated)
                updated
            } else note
            pushNote(toPush)
        }
    }

    private suspend fun applyReminderDoc(db: AppDatabase, docId: String, data: Map<String, Any?>) {
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

    private suspend fun removeReminderByCloudId(db: AppDatabase, cloudId: String) {
        val local = db.reminderDao().getReminderByCloudId(cloudId)
        if (local == null || local.cloudId.isEmpty()) return
        db.reminderDao().deleteReminder(local)
    }

    /** Resolves parentCloudId → parentId once all rows in [cloudDocs] exist locally. Safe to re-run: only writes when the link actually changed. */
    private suspend fun resolveReminderParentLinks(db: AppDatabase, cloudDocs: List<Pair<String, Map<String, Any?>>>) {
        for ((_, data) in cloudDocs) {
            val cloudId = (data["cloudId"] as? String)?.takeIf { it.isNotBlank() } ?: continue
            val parentCloudId = data["parentCloudId"] as? String ?: continue
            val child = db.reminderDao().getReminderByCloudId(cloudId) ?: continue
            val parent = db.reminderDao().getReminderByCloudId(parentCloudId) ?: continue
            if (child.parentId != parent.id) {
                db.reminderDao().updateReminder(child.copy(parentId = parent.id))
            }
        }
    }

    private fun reminderChangesFlow(): Flow<QuerySnapshot?> = callbackFlow {
        val uid = userId ?: return@callbackFlow awaitClose { }
        val listener = firestore.collection("users").document(uid)
            .collection("reminders")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "Reminder listener error", error); return@addSnapshotListener }
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                trySend(snapshot)
            }
        awaitClose { listener.remove() }
    }

    suspend fun collectReminderChanges(db: AppDatabase) {
        reminderChangesFlow().collect { snapshot ->
            if (snapshot == null) return@collect
            for (change in snapshot.documentChanges) {
                val docId = change.document.id
                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> applyReminderDoc(db, docId, change.document.data)
                    DocumentChange.Type.REMOVED -> removeReminderByCloudId(db, docId)
                }
            }
            // Re-resolve against the full current doc set, not just this batch's changes —
            // a parent and child can arrive in different snapshot fires.
            resolveReminderParentLinks(db, snapshot.documents.mapNotNull { d -> d.data?.let { d.id to it } })
        }
    }

    private suspend fun syncReminders(db: AppDatabase) {
        val cloudDocs = pullAllReminders()

        // First pass: insert/update all without resolving parentId
        for ((docId, data) in cloudDocs) {
            applyReminderDoc(db, docId, data)
        }

        // Second pass: resolve parentCloudId → parentId
        resolveReminderParentLinks(db, cloudDocs)

        // Assign cloudIds where missing (threading parentCloudId within the batch),
        // then push ALL local rows (see syncCategories).
        val allLocalReminders = db.reminderDao().getAllRemindersOneShot()
        val existingCloudIdsById = allLocalReminders.associate { it.id to it.cloudId }
        val toAssign = allLocalReminders.filter { it.cloudId.isEmpty() }
        for (reminder in resolvePendingReminderCloudIds(toAssign, existingCloudIdsById)) {
            val updated = reminder.copy(modifiedAt = System.currentTimeMillis())
            db.reminderDao().updateReminder(updated)
            pushReminder(updated)
        }
        for (reminder in allLocalReminders.filter { it.cloudId.isNotEmpty() }) {
            pushReminder(reminder)
        }
    }

    private suspend fun applyStudySessionDoc(db: AppDatabase, docId: String, data: Map<String, Any?>) {
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

    private fun studySessionChangesFlow(): Flow<List<DocumentChange>> = callbackFlow {
        val uid = userId ?: return@callbackFlow awaitClose { }
        val listener = firestore.collection("users").document(uid)
            .collection("study_sessions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "Study session listener error", error); return@addSnapshotListener }
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                trySend(snapshot.documentChanges)
            }
        awaitClose { listener.remove() }
    }

    suspend fun collectStudySessionChanges(db: AppDatabase) {
        studySessionChangesFlow().collect { changes ->
            for (change in changes) {
                val docId = change.document.id
                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> applyStudySessionDoc(db, docId, change.document.data)
                    // Study sessions aren't user-deletable; local timer stays source of truth.
                    DocumentChange.Type.REMOVED -> {}
                }
            }
        }
    }

    private suspend fun syncStudySessions(db: AppDatabase) {
        for ((docId, data) in pullAllStudySessions()) {
            applyStudySessionDoc(db, docId, data)
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

    private fun excludedAppChangesFlow(): Flow<List<DocumentChange>> = callbackFlow {
        val uid = userId ?: return@callbackFlow awaitClose { }
        val listener = firestore.collection("users").document(uid)
            .collection("excluded_apps")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "Excluded app listener error", error); return@addSnapshotListener }
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                trySend(snapshot.documentChanges)
            }
        awaitClose { listener.remove() }
    }

    suspend fun collectExcludedAppChanges(db: AppDatabase) {
        excludedAppChangesFlow().collect { changes ->
            for (change in changes) {
                // docId IS the packageName for this collection — no cloudId indirection.
                val packageName = change.document.id
                when (change.type) {
                    DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> db.excludedAppDao().excludeApp(ExcludedApp(packageName = packageName))
                    DocumentChange.Type.REMOVED -> db.excludedAppDao().includeApp(ExcludedApp(packageName = packageName))
                }
            }
        }
    }

    // ── Live screen-time listener (devices subtree) ──────────────────────────
    // Not routed through SyncCoordinator/Room: this feeds ScreenTimeViewModel's
    // UI-facing DeviceSession list directly, not a persisted entity.

    fun getOtherDevicesScreenTimeFlow(): Flow<List<DeviceSession>> = callbackFlow {
        val uid = userId ?: return@callbackFlow awaitClose { }
        val today = LocalDate.now().toString()
        val childListeners = mutableMapOf<String, ListenerRegistration>()
        val latestByDevice = mutableMapOf<String, DeviceSession>()

        fun emit() = trySend(latestByDevice.values.toList())

        val devicesListener = firestore.collection("users").document(uid)
            .collection("devices")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "Devices listener error", error); return@addSnapshotListener }
                if (snapshot == null) return@addSnapshotListener
                for (change in snapshot.documentChanges) {
                    val dId = change.document.id
                    if (dId == deviceId) continue
                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            if (childListeners.containsKey(dId)) continue
                            val dName = change.document.getString("deviceName") ?: dId
                            val reg = firestore.collection("users").document(uid)
                                .collection("devices").document(dId)
                                .collection("screen_time").document(today)
                                .addSnapshotListener { sessionSnap, sessionError ->
                                    if (sessionError != null) { Log.w(TAG, "Screen time listener error for $dId", sessionError); return@addSnapshotListener }
                                    if (sessionSnap != null && sessionSnap.metadata.hasPendingWrites()) return@addSnapshotListener
                                    if (sessionSnap != null && sessionSnap.exists()) {
                                        val millis = sessionSnap.getLong("durationMillis") ?: 0L
                                        latestByDevice[dId] = DeviceSession(dId, dName, today, millis, false)
                                        emit()
                                    }
                                }
                            childListeners[dId] = reg
                        }
                        DocumentChange.Type.REMOVED -> {
                            childListeners.remove(dId)?.remove()
                            latestByDevice.remove(dId)
                            emit()
                        }
                        DocumentChange.Type.MODIFIED -> {} // device display name changing live isn't required
                    }
                }
            }
        awaitClose {
            devicesListener.remove()
            childListeners.values.forEach { it.remove() }
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
