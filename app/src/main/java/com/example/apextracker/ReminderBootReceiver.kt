package com.example.apextracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Re-arms exact alarms after reboot, since AlarmManager alarms don't survive a device restart. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = ReminderSettings(appContext)
                if (settings.notificationsEnabled.first()) {
                    val allDayTime = settings.allDayNotificationTime.first()
                    val offsetMinutes = settings.specificTimeOffsetMinutes.first()
                    val reminderDao = AppDatabase.getDatabase(appContext).reminderDao()
                    val now = LocalDateTime.now()

                    reminderDao.getActiveReminders().first().forEach { reminder ->
                        val triggerTime = ReminderScheduler.computeTriggerTime(reminder, allDayTime, offsetMinutes)
                        if (triggerTime.isAfter(now)) {
                            ReminderScheduler.schedule(appContext, reminder, ReminderScheduler.toEpochMillis(triggerTime))
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
