package com.example.apextracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, 0)
        val reminderName = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_NAME) ?: "Reminder"
        val reminderDescription = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION)
        val reminderPriority = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_PRIORITY)

        val inputData = Data.Builder()
            .putString("reminder_name", reminderName)
            .putString("reminder_description", reminderDescription)
            .putLong("reminder_id", reminderId)
            .putString("reminder_priority", reminderPriority)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
    }
}
