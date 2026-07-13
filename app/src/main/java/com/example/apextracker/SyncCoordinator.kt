package com.example.apextracker

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the lifecycle of the live Firestore listeners that keep Room in sync with cloud
 * changes made on other devices. Started once per sign-in (after performInitialSync
 * completes), stopped on sign-out. SupervisorJob so one entity's listener failing
 * doesn't take down the others.
 */
object SyncCoordinator {
    private const val TAG = "SyncCoordinator"
    private var job: Job? = null

    fun start(firebaseManager: FirebaseManager, db: AppDatabase) {
        stop()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        job = scope.coroutineContext[Job]
        scope.launch { firebaseManager.collectCategoryChanges(db) }
        scope.launch { firebaseManager.collectBudgetItemChanges(db) }
        scope.launch { firebaseManager.collectSubscriptionChanges(db) }
        scope.launch { firebaseManager.collectNoteChanges(db) }
        scope.launch { firebaseManager.collectReminderChanges(db) }
        scope.launch { firebaseManager.collectStudySessionChanges(db) }
        scope.launch { firebaseManager.collectExcludedAppChanges(db) }
        Log.d(TAG, "Listeners started")
    }

    fun stop() {
        job?.cancel()
        job = null
        Log.d(TAG, "Listeners removed")
    }
}
