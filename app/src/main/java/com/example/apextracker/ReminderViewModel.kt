package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val reminderDao = AppDatabase.getDatabase(application).reminderDao()
    private val workManager = WorkManager.getInstance(application)
    private val settings = ReminderSettings(application)

    val activeReminders: Flow<List<Reminder>> = reminderDao.getActiveReminders()
    val completedReminders: Flow<List<Reminder>> = reminderDao.getCompletedReminders()
    
    val notificationsEnabled = settings.notificationsEnabled
    val allDayNotificationTime = settings.allDayNotificationTime
    val specificTimeOffsetMinutes = settings.specificTimeOffsetMinutes

    fun addReminder(name: String, date: LocalDate, time: LocalTime?, description: String?) {
        viewModelScope.launch {
            val reminder = Reminder(
                name = name,
                date = date,
                time = time,
                description = description
            )
            val id = reminderDao.insert(reminder)
            scheduleNotification(reminder.copy(id = id))
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.update(reminder)
            if (reminder.isCompleted) {
                cancelNotification(reminder.id)
            } else {
                scheduleNotification(reminder)
            }
        }
    }

    fun toggleCompletion(reminder: Reminder) {
        viewModelScope.launch {
            val updated = reminder.copy(isCompleted = !reminder.isCompleted)
            reminderDao.update(updated)
            if (updated.isCompleted) {
                cancelNotification(updated.id)
            } else {
                scheduleNotification(updated)
            }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.delete(reminder)
            cancelNotification(reminder.id)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setNotificationsEnabled(enabled)
            if (!enabled) {
                workManager.cancelAllWorkByTag("reminder")
            } else {
                // Re-schedule all active reminders
                activeReminders.first().forEach { scheduleNotification(it) }
            }
        }
    }

    fun setAllDayTime(time: LocalTime) {
        viewModelScope.launch {
            settings.setAllDayNotificationTime(time)
            // Re-schedule active reminders to apply new time
            activeReminders.first().forEach { scheduleNotification(it) }
        }
    }

    fun setOffset(minutes: Int) {
        viewModelScope.launch {
            settings.setSpecificTimeOffsetMinutes(minutes)
            // Re-schedule active reminders to apply new offset
            activeReminders.first().forEach { scheduleNotification(it) }
        }
    }

    private suspend fun scheduleNotification(reminder: Reminder) {
        cancelNotification(reminder.id)
        
        if (!settings.notificationsEnabled.first()) return
        if (reminder.isCompleted) return

        val now = LocalDateTime.now()
        val notificationTime = if (reminder.time == null) {
            // All day reminder
            val prefTime = settings.allDayNotificationTime.first()
            LocalDateTime.of(reminder.date, prefTime)
        } else {
            // Specific time reminder
            val offset = settings.specificTimeOffsetMinutes.first()
            LocalDateTime.of(reminder.date, reminder.time).minusMinutes(offset.toLong())
        }

        if (notificationTime.isBefore(now)) return

        val delay = Duration.between(now, notificationTime).toMillis()

        val data = Data.Builder()
            .putString("reminder_name", reminder.name)
            .putString("reminder_description", reminder.description)
            .putLong("reminder_id", reminder.id)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("reminder")
            .addTag("reminder_${reminder.id}")
            .build()

        workManager.enqueueUniqueWork(
            "reminder_${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun cancelNotification(reminderId: Long) {
        workManager.cancelUniqueWork("reminder_$reminderId")
    }
}
