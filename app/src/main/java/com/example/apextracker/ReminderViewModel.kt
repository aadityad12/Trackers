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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val reminderDao = database.reminderDao()
    private val settingsRepository = ReminderSettings(application)

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
                recurrence = recurrence
            )
            val id = reminderDao.insertReminder(reminder)
            scheduleIfNeeded(reminder.copy(id = id))
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.updateReminder(reminder)
            scheduleIfNeeded(reminder)
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

    fun toggleCompletion(reminder: Reminder) {
        viewModelScope.launch {
            if (!reminder.isCompleted && reminder.recurrence != null) {
                // Handle completion of a recurring reminder
                handleRecurringCompletion(reminder)
            } else {
                val updated = reminder.copy(isCompleted = !reminder.isCompleted)
                reminderDao.updateReminder(updated)
                scheduleIfNeeded(updated)
            }
        }
    }

    private suspend fun handleRecurringCompletion(reminder: Reminder) {
        val recurrence = reminder.recurrence ?: return
        val nextOccurrencesCompleted = reminder.occurrencesCompleted + 1
        
        // Check if we should generate the next occurrence
        val shouldContinue = when (recurrence.endType) {
            RecurrenceEndType.NEVER -> true
            RecurrenceEndType.UNTIL_DATE -> {
                val nextDate = calculateNextDate(reminder.date, recurrence)
                nextDate != null && (recurrence.endDate == null || !nextDate.isAfter(recurrence.endDate))
            }
            RecurrenceEndType.AFTER_OCCURRENCES -> {
                recurrence.endOccurrences == null || nextOccurrencesCompleted < recurrence.endOccurrences
            }
        }

        if (shouldContinue) {
            val nextDate = calculateNextDate(reminder.date, recurrence)
            if (nextDate != null) {
                // Insert the next instance
                val nextReminder = reminder.copy(
                    id = 0,
                    date = nextDate,
                    isCompleted = false,
                    occurrencesCompleted = nextOccurrencesCompleted
                )
                val nextId = reminderDao.insertReminder(nextReminder)
                scheduleIfNeeded(nextReminder.copy(id = nextId))
            }
        }

        // Mark current one as completed
        reminderDao.updateReminder(reminder.copy(isCompleted = true))
        ReminderScheduler.cancel(getApplication(), reminder.id)
    }

    private fun calculateNextDate(currentDate: LocalDate, recurrence: Recurrence): LocalDate? {
        return when (recurrence.frequency) {
            RecurrenceFrequency.DAILY -> currentDate.plusDays(1)
            RecurrenceFrequency.WEEKLY -> currentDate.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> currentDate.plusMonths(1)
            RecurrenceFrequency.YEARLY -> currentDate.plusYears(1)
            RecurrenceFrequency.CUSTOM -> {
                val days = recurrence.customDays ?: return null
                if (days.isEmpty()) return null
                
                var next = currentDate.plusDays(1)
                // Search for the next day of week in the set
                repeat(7) {
                    if (days.contains(next.dayOfWeek)) return next
                    next = next.plusDays(1)
                }
                null
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.deleteReminder(reminder)
            ReminderScheduler.cancel(getApplication(), reminder.id)
        }
    }

    fun deleteReminders(ids: List<Long>) {
        viewModelScope.launch {
            reminderDao.deleteRemindersByIds(ids)
            ids.forEach { ReminderScheduler.cancel(getApplication(), it) }
        }
    }

    fun clearAllCompleted() {
        viewModelScope.launch {
            reminderDao.clearAllCompleted()
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
