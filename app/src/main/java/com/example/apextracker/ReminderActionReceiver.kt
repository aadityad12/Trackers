package com.example.apextracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate

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
        val reminderName = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_NAME) ?: "Reminder"
        val reminderDescription = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION)

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
                WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
            }
            ACTION_SNOOZE -> {
                // Ephemeral — no DB write. If the device reboots mid-snooze, ReminderBootReceiver
                // re-arms from the reminder's real due time (not the snooze), so the notification
                // could fire early after a reboot. Accepted tradeoff per Issue #41.
                val stub = Reminder(id = reminderId, name = reminderName, description = reminderDescription, date = LocalDate.now())
                val triggerAtMillis = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000
                ReminderScheduler.schedule(context, stub, triggerAtMillis)
            }
        }
    }
}
