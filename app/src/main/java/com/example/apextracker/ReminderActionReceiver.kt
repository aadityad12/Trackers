package com.example.apextracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "Done" and "Snooze 10 min" notification actions from [ReminderWorker]. */
class ReminderActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ReminderActionReceiver"
        const val ACTION_COMPLETE = "com.example.apextracker.ACTION_REMINDER_COMPLETE"
        const val ACTION_SNOOZE = "com.example.apextracker.ACTION_REMINDER_SNOOZE"
        const val SNOOZE_MINUTES = 10L
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive action=${intent.action}")
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, 0)

        // Dismiss the notification immediately regardless of what the action does next.
        NotificationManagerCompat.from(context).cancel(reminderId.toInt())

        when (intent.action) {
            ACTION_COMPLETE -> {
                // DB + network work — enqueue via WorkManager rather than a raw coroutine so it
                // survives the receiver's short-lived execution window and the app process
                // being dead (this is exactly why #41 depends on the shared completeReminder()
                // handler rather than duplicating ReminderViewModel's logic here).
                val inputData = Data.Builder().putLong("reminder_id", reminderId).build()
                val workRequest = OneTimeWorkRequestBuilder<ReminderCompleteWorker>()
                    .setInputData(inputData)
                    .build()
                // Unique per reminder so a repeated Done tap can't run two workers at once.
                // KEEP: the in-flight worker is already completing this exact id, and
                // completeReminder() is a no-op the second time anyway.
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    "complete_reminder_$reminderId",
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            }
            ACTION_SNOOZE -> {
                // Ephemeral — no DB write. If the device reboots mid-snooze, ReminderBootReceiver
                // re-arms from the reminder's real due time (not the snooze), so the notification
                // could fire early after a reboot. Accepted tradeoff per Issue #41.
                snooze(context.applicationContext, reminderId)
            }
        }
    }

    /**
     * Re-arms this reminder's alarm [SNOOZE_MINUTES] out, using the row's current state rather
     * than the notification's (possibly stale) extras. The notification can outlive the reminder
     * — completed or deleted in-app while still on screen — and arming an alarm off the extras
     * would fire a phantom notification for something the user already dealt with (Issue #64).
     */
    private fun snooze(context: Context, reminderId: Long) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = AppDatabase.getDatabase(context).reminderDao().getReminderById(reminderId)
                if (reminder == null || reminder.isCompleted) {
                    Log.d(TAG, "Ignoring snooze for reminder $reminderId — already completed or deleted")
                    return@launch
                }
                ReminderScheduler.schedule(context, reminder, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to snooze reminder $reminderId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
