package com.example.apextracker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Runs the shared [completeReminder] flow for the notification's "Done" action. */
class ReminderCompleteWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong("reminder_id", 0)
        val db = AppDatabase.getDatabase(applicationContext)
        val firebaseManager = FirebaseManager(applicationContext)
        completeReminder(applicationContext, db, firebaseManager, reminderId)
        return Result.success()
    }
}
