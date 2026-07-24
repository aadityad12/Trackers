package com.example.apextracker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BACKUP_TAG = "BackupManager"

/** Reads every table into a [BackupData] snapshot (Issue #121). [now] is passed in for testability. */
suspend fun exportBackup(db: AppDatabase, now: String): BackupData = withContext(Dispatchers.IO) {
    BackupData(
        exportedAt = now,
        budgetItems = db.budgetDao().getAllItemsOneShot(),
        categories = db.categoryDao().getAllCategoriesOneShot(),
        subscriptions = db.subscriptionDao().getAllSubscriptionsSync(),
        studySessions = db.studySessionDao().getAllSessionsOneShot(),
        screenTimeSessions = db.screenTimeSessionDao().getAllSessionsOneShot(),
        excludedApps = db.excludedAppDao().getAllExcludedAppsOneShot(),
        reminders = db.reminderDao().getAllRemindersOneShot(),
        notes = db.noteDao().getAllNotesOneShot(),
        goals = db.goalDao().getAllGoalsOneShot(),
        goalCompletions = db.goalCompletionDao().getAllCompletionsOneShot(),
        appUsageLimits = db.appUsageLimitDao().getLimitsOneShot()
    )
}

/**
 * Replaces the entire local dataset with [data] (Issue #121) — "restore from backup" semantics, so
 * the whole thing runs in one Room transaction: every table is cleared, then re-inserted with the
 * backup's own primary keys (preserving cross-references like BudgetItem.categoryId). A failure
 * rolls the transaction back, leaving the existing data untouched. It does NOT restore note image
 * files (those aren't in the DB); the note rows survive but their attachment thumbnails will be
 * missing, same device-local caveat as Issue #127.
 */
suspend fun restoreBackup(db: AppDatabase, data: BackupData) = withContext(Dispatchers.IO) {
    db.withTransaction {
        db.budgetDao().clearAll()
        db.categoryDao().clearAll()
        db.subscriptionDao().clearAll()
        db.studySessionDao().clearAll()
        db.screenTimeSessionDao().clearAll()
        db.excludedAppDao().clearAll()
        db.reminderDao().clearAll()
        db.noteDao().clearAll()
        db.goalDao().clearAll()
        db.goalCompletionDao().clearAll()
        db.appUsageLimitDao().clearAll()

        data.categories.forEach { db.categoryDao().insertCategory(it) }
        data.budgetItems.forEach { db.budgetDao().insertItem(it) }
        data.subscriptions.forEach { db.subscriptionDao().insertSubscription(it) }
        data.studySessions.forEach { db.studySessionDao().insertSession(it) }
        data.screenTimeSessions.forEach { db.screenTimeSessionDao().insertSession(it) }
        data.excludedApps.forEach { db.excludedAppDao().excludeApp(it) }
        data.reminders.forEach { db.reminderDao().insertReminder(it) }
        data.notes.forEach { db.noteDao().insert(it) }
        data.goals.forEach { db.goalDao().insertGoal(it) }
        data.goalCompletions.forEach { db.goalCompletionDao().upsert(it) }
        data.appUsageLimits.forEach { db.appUsageLimitDao().setLimit(it) }
    }
}

/** Writes backup JSON to the SAF [uri] the user chose (ACTION_CREATE_DOCUMENT). */
suspend fun writeBackupToUri(context: Context, uri: Uri, json: String): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            ?: return@withContext false
        true
    } catch (e: Exception) {
        Log.w(BACKUP_TAG, "Failed to write backup to $uri", e)
        false
    }
}

/** Reads a backup file the user picked (ACTION_OPEN_DOCUMENT); null on read failure. */
suspend fun readBackupFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) {
        Log.w(BACKUP_TAG, "Failed to read backup from $uri", e)
        null
    }
}
