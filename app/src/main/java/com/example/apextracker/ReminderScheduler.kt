package com.example.apextracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
    const val EXTRA_REMINDER_PRIORITY = "reminder_priority"

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

    /**
     * The moment this reminder's alarm should actually fire, or null if it shouldn't fire at all.
     *
     * The offset means "notify me N minutes before the task". When the task is nearer than N
     * minutes, [computeTriggerTime] lands in the past — and firing nothing is the worst possible
     * reading of that, since the task itself is still coming up. It also isn't a corner case: the
     * offset defaults to 30 minutes, so "set a reminder for 10 minutes from now" would silently
     * never notify. So clamp to [now] (notify immediately) instead, and only give up once the
     * task's own due time has passed (Issue #80).
     *
     * All-day reminders take no offset — [computeTriggerTime] is already their exact notification
     * time — so a past trigger there genuinely means the moment has been and gone: nothing to
     * clamp to, return null.
     */
    fun resolveTriggerTime(
        reminder: Reminder,
        allDayNotificationTime: LocalTime,
        specificTimeOffsetMinutes: Int,
        now: LocalDateTime
    ): LocalDateTime? {
        val trigger = computeTriggerTime(reminder, allDayNotificationTime, specificTimeOffsetMinutes)
        if (trigger.isAfter(now)) return trigger
        val dueTime = LocalDateTime.of(reminder.date, reminder.time ?: return null)
        return if (dueTime.isAfter(now)) now else null
    }

    /** True when exact alarms are available (always below API 31, permission-gated from 31 on). */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * Intent for the system screen where the user grants this app the exact-alarm special
     * permission (API 31+; denied by default from API 33). Null below API 31 where no grant
     * is needed.
     */
    fun requestExactAlarmIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))
    }

    fun schedule(context: Context, reminder: Reminder, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, reminder.id, reminder.name, reminder.description, reminder.priority)

        if (canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, reminderId, name = "", description = null, priority = null)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(
        context: Context,
        reminderId: Long,
        name: String,
        description: String?,
        priority: String?
    ): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_NAME, name)
            putExtra(EXTRA_REMINDER_DESCRIPTION, description)
            putExtra(EXTRA_REMINDER_PRIORITY, priority)
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
