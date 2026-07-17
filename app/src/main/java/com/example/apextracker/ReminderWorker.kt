package com.example.apextracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val reminderName = inputData.getString("reminder_name") ?: "Reminder"
        val reminderDescription = inputData.getString("reminder_description")
        val reminderId = inputData.getLong("reminder_id", 0)

        sendNotification(reminderName, reminderDescription, reminderId)

        return Result.success()
    }

    private fun sendNotification(name: String, description: String?, id: Long) {
        val channelId = "reminder_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                this.description = "Notifications for tasks and reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tapping the notification opens the app on the Reminders screen. Request code is the
        // reminder id so concurrent notifications don't share (and overwrite) one PendingIntent.
        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "reminders")
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            id.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Distinct request codes (id vs -id) so Done and Snooze don't share one PendingIntent.
        val completeIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_COMPLETE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, id)
            putExtra(ReminderScheduler.EXTRA_REMINDER_NAME, name)
            putExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION, description)
        }
        // ONE_SHOT so a rapid double-tap on Done can't deliver the broadcast twice. This is only
        // the outer guard — completeReminder() claims the completion atomically in the DB, which
        // is what actually holds when the in-app checkbox races the notification (Issue #63).
        val completePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            id.toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, id)
            putExtra(ReminderScheduler.EXTRA_REMINDER_NAME, name)
            putExtra(ReminderScheduler.EXTRA_REMINDER_DESCRIPTION, description)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            (-id).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Reminder: $name")
            .setContentText(description ?: "Your task is due!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.checkbox_on_background, applicationContext.getString(R.string.reminders_notif_done), completePendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, applicationContext.getString(R.string.reminders_notif_snooze), snoozePendingIntent)

        notificationManager.notify(id.toInt(), builder.build())
    }
}
