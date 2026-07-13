package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "ReminderViewModel"
    }

    private val database = AppDatabase.getDatabase(application)
    private val reminderDao = database.reminderDao()
    private val settingsRepository = ReminderSettings(application)
    private val firebaseManager = FirebaseManager(application)

    val activeReminders: Flow<List<Reminder>> = reminderDao.getActiveReminders()
    val completedReminders: Flow<List<Reminder>> = reminderDao.getCompletedReminders()
    
    val notificationsEnabled: Flow<Boolean> = settingsRepository.notificationsEnabled
    val allDayNotificationTime: Flow<LocalTime> = settingsRepository.allDayNotificationTime
    val specificTimeOffsetMinutes: Flow<Int> = settingsRepository.specificTimeOffsetMinutes

    fun addReminder(
        name: String,
        date: LocalDate,
        time: LocalTime?,
        description: String?,
        recurrence: Recurrence? = null
    ) {
        viewModelScope.launch {
            val reminder = Reminder(
                name = name,
                date = date,
                time = time,
                description = description,
                recurrence = recurrence,
                cloudId = UUID.randomUUID().toString(),
                modifiedAt = System.currentTimeMillis()
            )
            val id = reminderDao.insertReminder(reminder)
            scheduleIfNeeded(reminder.copy(id = id))
            safeCloudCall(TAG, "push reminder") {
                firebaseManager.pushReminder(reminder)
            }
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            val updated = reminder.copy(
                cloudId = reminder.cloudId.ifEmpty { UUID.randomUUID().toString() },
                modifiedAt = System.currentTimeMillis()
            )
            reminderDao.updateReminder(updated)
            scheduleIfNeeded(updated)
            safeCloudCall(TAG, "update reminder") {
                firebaseManager.pushReminder(updated)
            }
        }
    }

    /** Schedules (or cancels, if completed/disabled/past-due) the exact alarm for a reminder. */
    private suspend fun scheduleIfNeeded(reminder: Reminder) {
        val context = getApplication<Application>()
        if (reminder.isCompleted || !settingsRepository.notificationsEnabled.first()) {
            ReminderScheduler.cancel(context, reminder.id)
            return
        }
        val allDayTime = settingsRepository.allDayNotificationTime.first()
        val offsetMinutes = settingsRepository.specificTimeOffsetMinutes.first()
        val triggerTime = ReminderScheduler.computeTriggerTime(reminder, allDayTime, offsetMinutes)
        if (triggerTime.isAfter(LocalDateTime.now())) {
            ReminderScheduler.schedule(context, reminder, ReminderScheduler.toEpochMillis(triggerTime))
        } else {
            ReminderScheduler.cancel(context, reminder.id)
        }
    }

    private suspend fun rescheduleAllActive() {
        activeReminders.first().forEach { scheduleIfNeeded(it) }
    }

    /** Re-arms every active reminder — used after the exact-alarm permission is granted, so
     *  alarms scheduled inexactly while it was denied become exact. */
    fun rescheduleAll() {
        viewModelScope.launch { rescheduleAllActive() }
    }

    // Reminder ids with a toggle currently being processed. Only touched on the main thread
    // (Compose click handlers + viewModelScope), so no synchronization is needed. Guards against
    // rapid double-taps launching two coroutines that both see the stale "incomplete" snapshot
    // and insert two next occurrences for a recurring reminder.
    private val togglesInFlight = mutableSetOf<Long>()

    fun toggleCompletion(reminder: Reminder) {
        if (!togglesInFlight.add(reminder.id)) return
        viewModelScope.launch {
            try {
                // The UI hands us a possibly-stale snapshot; act on the row's current state.
                val fresh = reminderDao.getReminderById(reminder.id) ?: return@launch
                if (!fresh.isCompleted && fresh.recurrence != null) {
                    // Handle completion of a recurring reminder
                    handleRecurringCompletion(fresh)
                } else {
                    val updated = fresh.copy(
                        isCompleted = !fresh.isCompleted,
                        cloudId = fresh.cloudId.ifEmpty { UUID.randomUUID().toString() },
                        modifiedAt = System.currentTimeMillis()
                    )
                    reminderDao.updateReminder(updated)
                    scheduleIfNeeded(updated)
                    safeCloudCall(TAG, "toggle reminder completion") {
                        firebaseManager.pushReminder(updated)
                    }
                }
            } finally {
                togglesInFlight.remove(reminder.id)
            }
        }
    }

    private suspend fun handleRecurringCompletion(reminder: Reminder) {
        // Anchor monthly/yearly chains to the original day-of-month before advancing,
        // so short-month clamping (Jan 31 → Feb 28) doesn't drift permanently.
        val recurrence = reminder.recurrence?.withAnchorFrom(reminder.date) ?: return
        // Skipped (missed) periods don't count toward AFTER_OCCURRENCES — only actual completions do.
        val nextOccurrencesCompleted = reminder.occurrencesCompleted + 1

        // Next occurrence on the grid after today: completing an overdue reminder catches the
        // chain up to the future instead of inserting already-past occurrences one by one.
        val nextDate = calculateNextOccurrenceAfter(reminder.date, recurrence, LocalDate.now())

        // Check if we should generate the next occurrence
        val shouldContinue = when (recurrence.endType) {
            RecurrenceEndType.NEVER -> true
            RecurrenceEndType.UNTIL_DATE -> {
                nextDate != null && (recurrence.endDate == null || !nextDate.isAfter(recurrence.endDate))
            }
            RecurrenceEndType.AFTER_OCCURRENCES -> {
                recurrence.endOccurrences == null || nextOccurrencesCompleted < recurrence.endOccurrences
            }
        }

        if (shouldContinue) {
            if (nextDate != null) {
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
                scheduleIfNeeded(nextReminder.copy(id = nextId))
                safeCloudCall(TAG, "push next recurring reminder") {
                    firebaseManager.pushReminder(nextReminder)
                }
            }
        }

        // Mark current one as completed
        val completed = reminder.copy(
            isCompleted = true,
            cloudId = reminder.cloudId.ifEmpty { UUID.randomUUID().toString() },
            modifiedAt = System.currentTimeMillis()
        )
        reminderDao.updateReminder(completed)
        ReminderScheduler.cancel(getApplication(), reminder.id)
        safeCloudCall(TAG, "push completed recurring reminder") {
            firebaseManager.pushReminder(completed)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.deleteReminder(reminder)
            ReminderScheduler.cancel(getApplication(), reminder.id)
            safeCloudCall(TAG, "delete reminder") {
                firebaseManager.deleteReminder(reminder.cloudId)
            }
        }
    }

    /** Undo for [deleteReminder]: re-inserts the preserved reminder unchanged (REPLACE
     *  keeps its Room id) and re-arms its alarm. The cloud delete has already been
     *  pushed by then; re-pushing the same cloudId recreates the doc. */
    fun restoreReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.insertReminder(reminder)
            scheduleIfNeeded(reminder)
            safeCloudCall(TAG, "restore reminder") {
                firebaseManager.pushReminder(reminder)
            }
        }
    }

    fun deleteReminders(ids: List<Long>) {
        viewModelScope.launch {
            // Capture cloudIds before the rows are gone
            val victims = reminderDao.getRemindersByIds(ids)
            reminderDao.deleteRemindersByIds(ids)
            ids.forEach { ReminderScheduler.cancel(getApplication(), it) }
            safeCloudCall(TAG, "delete reminders") {
                victims.forEach { firebaseManager.deleteReminder(it.cloudId) }
            }
        }
    }

    fun clearAllCompleted() {
        viewModelScope.launch {
            // Capture cloudIds before the rows are gone
            val victims = reminderDao.getCompletedRemindersOneShot()
            reminderDao.clearAllCompleted()
            safeCloudCall(TAG, "clear completed reminders") {
                victims.forEach { firebaseManager.deleteReminder(it.cloudId) }
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
            if (enabled) {
                rescheduleAllActive()
            } else {
                activeReminders.first().forEach { ReminderScheduler.cancel(getApplication(), it.id) }
            }
        }
    }

    fun setAllDayTime(time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setAllDayNotificationTime(time)
            rescheduleAllActive()
        }
    }

    fun setOffset(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setSpecificTimeOffsetMinutes(minutes)
            rescheduleAllActive()
        }
    }
}
