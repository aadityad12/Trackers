package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Unfiltered lists stay available so the UI can tell "no reminders" from "no matches",
    // exactly like NoteViewModel's activeNotes/filteredNotes split (Issue #123).
    val allActiveReminders: Flow<List<Reminder>> = reminderDao.getActiveReminders()
    val allCompletedReminders: Flow<List<Reminder>> = reminderDao.getCompletedReminders()

    val activeReminders: Flow<List<Reminder>> =
        combine(allActiveReminders, _searchQuery) { list, query -> filterReminders(list, query) }
    val completedReminders: Flow<List<Reminder>> =
        combine(allCompletedReminders, _searchQuery) { list, query -> filterReminders(list, query) }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
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
            scheduleReminderIfNeeded(getApplication(), reminder.copy(id = id))
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
            scheduleReminderIfNeeded(getApplication(), updated)
            safeCloudCall(TAG, "update reminder") {
                firebaseManager.pushReminder(updated)
            }
        }
    }

    private suspend fun rescheduleAllActive() {
        activeReminders.first().forEach { scheduleReminderIfNeeded(getApplication(), it) }
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
                if (!fresh.isCompleted) {
                    // Shared with the notification's Done action (Issue #41) so both paths
                    // agree on recurrence advancement + alarm cancel + cloud push.
                    completeReminder(getApplication(), database, firebaseManager, fresh.id)
                } else {
                    val updated = fresh.copy(
                        isCompleted = false,
                        cloudId = fresh.cloudId.ifEmpty { UUID.randomUUID().toString() },
                        modifiedAt = System.currentTimeMillis()
                    )
                    reminderDao.updateReminder(updated)
                    scheduleReminderIfNeeded(getApplication(), updated)
                    safeCloudCall(TAG, "toggle reminder completion") {
                        firebaseManager.pushReminder(updated)
                    }
                }
            } finally {
                togglesInFlight.remove(reminder.id)
            }
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
            scheduleReminderIfNeeded(getApplication(), reminder)
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
