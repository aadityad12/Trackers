package com.example.apextracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules and cancels exact-time alarms for reminder notifications.
 * AlarmManager (not WorkManager alone) is used because WorkManager's minimum-latency
 * scheduling isn't precise enough for a due-time notification; the alarm wakes the
 * device and hands off to [ReminderAlarmReceiver], which enqueues [ReminderWorker].
 */
object ReminderScheduler {
    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_REMINDER_NAME = "reminder_name"
    const val EXTRA_REMINDER_DESCRIPTION = "reminder_description"

    fun computeTriggerTime(
        reminder: Reminder,
        allDayNotificationTime: LocalTime,
        specificTimeOffsetMinutes: Int
    ): LocalDateTime {
        return if (reminder.time == null) {
            LocalDateTime.of(reminder.date, allDayNotificationTime)
        } else {
            LocalDateTime.of(reminder.date, reminder.time).minusMinutes(specificTimeOffsetMinutes.toLong())
        }
    }

    fun schedule(context: Context, reminder: Reminder, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, reminder.id, reminder.name, reminder.description)

        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, reminderId, name = "", description = null)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(context: Context, reminderId: Long, name: String, description: String?): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_NAME, name)
            putExtra(EXTRA_REMINDER_DESCRIPTION, description)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun toEpochMillis(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
