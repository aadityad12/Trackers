package com.example.apextracker

import android.content.Context
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val TAG = "ReminderCompletion"

/**
 * Schedules (or cancels, if completed/disabled/past-due) the exact alarm for a reminder.
 * Shared by ReminderViewModel and the notification Done/Snooze actions (Issue #41) so both
 * paths agree on when a reminder should actually alarm.
 */
suspend fun scheduleReminderIfNeeded(context: Context, reminder: Reminder) {
    val settings = ReminderSettings(context)
    if (reminder.isCompleted || !settings.notificationsEnabled.first()) {
        ReminderScheduler.cancel(context, reminder.id)
        return
    }
    val allDayTime = settings.allDayNotificationTime.first()
    val offsetMinutes = settings.specificTimeOffsetMinutes.first()
    val triggerTime = ReminderScheduler.computeTriggerTime(reminder, allDayTime, offsetMinutes)
    if (triggerTime.isAfter(LocalDateTime.now())) {
        ReminderScheduler.schedule(context, reminder, ReminderScheduler.toEpochMillis(triggerTime))
    } else {
        ReminderScheduler.cancel(context, reminder.id)
    }
}

/**
 * Completes a reminder — the same result whether the user checks it off in-app or taps "Done"
 * on the notification (works with the app process dead): marks it done and, for a recurring
 * reminder, generates the next occurrence (respecting end conditions, anchored to the original
 * day-of-month). Both paths push to cloud. No-ops if the reminder is already gone or completed
 * (stale notification action, double-tap, etc).
 */
suspend fun completeReminder(context: Context, db: AppDatabase, firebaseManager: FirebaseManager, reminderId: Long) {
    val reminderDao = db.reminderDao()
    val fresh = reminderDao.getReminderById(reminderId) ?: return
    if (fresh.isCompleted) return

    val completed = fresh.copy(
        isCompleted = true,
        cloudId = fresh.cloudId.ifEmpty { UUID.randomUUID().toString() },
        modifiedAt = System.currentTimeMillis()
    )
    // Claim the completion atomically before doing anything else. Two callers can reach here
    // concurrently — the notification's Done worker and the in-app checkbox, in different
    // processes — and both would see isCompleted = false above; ReminderViewModel's in-memory
    // guard only covers its own toggles. Whoever loses the compare-and-set stops here, so a
    // recurring reminder can never insert two next occurrences (Issue #63).
    if (reminderDao.markCompletedIfActive(completed.id, completed.cloudId, completed.modifiedAt) == 0) return

    ReminderScheduler.cancel(context, completed.id)
    safeCloudCall(TAG, "complete reminder") {
        firebaseManager.pushReminder(completed)
    }

    if (fresh.recurrence != null) {
        insertNextOccurrence(context, db, firebaseManager, fresh)
    }
}

/** Inserts the next instance of a recurring reminder whose current instance was just completed. */
private suspend fun insertNextOccurrence(context: Context, db: AppDatabase, firebaseManager: FirebaseManager, reminder: Reminder) {
    val reminderDao = db.reminderDao()
    // Anchor monthly/yearly chains to the original day-of-month before advancing,
    // so short-month clamping (Jan 31 → Feb 28) doesn't drift permanently.
    val recurrence = reminder.recurrence?.withAnchorFrom(reminder.date) ?: return
    // Skipped (missed) periods don't count toward AFTER_OCCURRENCES — only actual completions do.
    val nextOccurrencesCompleted = reminder.occurrencesCompleted + 1

    // Next occurrence on the grid after today: completing an overdue reminder catches the
    // chain up to the future instead of inserting already-past occurrences one by one.
    val nextDate = calculateNextOccurrenceAfter(reminder.date, recurrence, LocalDate.now())

    val shouldContinue = when (recurrence.endType) {
        RecurrenceEndType.NEVER -> true
        RecurrenceEndType.UNTIL_DATE -> {
            nextDate != null && (recurrence.endDate == null || !nextDate.isAfter(recurrence.endDate))
        }
        RecurrenceEndType.AFTER_OCCURRENCES -> {
            recurrence.endOccurrences == null || nextOccurrencesCompleted < recurrence.endOccurrences
        }
    }

    if (shouldContinue && nextDate != null) {
        // Insert the next instance with its own cloud identity
        // (parentId/parentCloudId are inherited via copy, keeping chain semantics)
        val nextReminder = reminder.copy(
            id = 0,
            date = nextDate,
            isCompleted = false,
            recurrence = recurrence,
            occurrencesCompleted = nextOccurrencesCompleted,
            cloudId = UUID.randomUUID().toString(),
            modifiedAt = System.currentTimeMillis()
        )
        val nextId = reminderDao.insertReminder(nextReminder)
        scheduleReminderIfNeeded(context, nextReminder.copy(id = nextId))
        safeCloudCall(TAG, "push next recurring reminder") {
            firebaseManager.pushReminder(nextReminder)
        }
    }
}
