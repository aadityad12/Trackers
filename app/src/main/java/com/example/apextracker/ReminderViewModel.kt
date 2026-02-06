package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val reminderDao = AppDatabase.getDatabase(application).reminderDao()

    val activeReminders: Flow<List<Reminder>> = reminderDao.getActiveReminders()
    val completedReminders: Flow<List<Reminder>> = reminderDao.getCompletedReminders()

    fun addReminder(name: String, date: LocalDate, time: LocalTime?, description: String?) {
        viewModelScope.launch {
            val reminder = Reminder(
                name = name,
                date = date,
                time = time,
                description = description
            )
            reminderDao.insert(reminder)
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.update(reminder)
        }
    }

    fun toggleCompletion(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.update(reminder.copy(isCompleted = !reminder.isCompleted))
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.delete(reminder)
        }
    }
}
