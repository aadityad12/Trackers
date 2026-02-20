package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayOverview(
    val date: LocalDate,
    val pendingReminders: List<Reminder>,
    val completedReminders: List<Reminder>,
    val missedReminders: List<Reminder>,
    val totalSpent: Double,
    val screenTimeMinutes: Long,
    val studyTimeMinutes: Long
)

class OverviewViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val reminderDao = db.reminderDao()
    private val budgetDao = db.budgetDao()
    private val studyDao = db.studySessionDao()
    private val screenTimeDao = db.screenTimeSessionDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val dayOverview: StateFlow<DayOverview?> = _selectedDate.flatMapLatest { date ->
        combine(
            reminderDao.getActiveReminders(),
            reminderDao.getCompletedReminders(),
            budgetDao.getAllItems(),
            studyDao.getAllSessions(),
            screenTimeDao.getAllSessions()
        ) { activeRem, compRem, budgetItems, studySessions, screenSessions ->
            
            val dayActive = activeRem.filter { it.date == date }
            val dayCompleted = compRem.filter { it.date == date }
            
            val missed = if (date < LocalDate.now()) {
                activeRem.filter { it.date == date }
            } else if (date == LocalDate.now()) {
                // For today, "missed" are active reminders from previous days
                activeRem.filter { it.date < date }
            } else {
                emptyList()
            }

            val spent = budgetItems.filter { it.date == date }.sumOf { it.amount }
            val study = (studySessions.find { it.date == date }?.durationSeconds ?: 0L) / 60
            val screen = (screenSessions.find { it.date == date }?.durationMillis ?: 0L) / 60000

            DayOverview(
                date = date,
                pendingReminders = dayActive,
                completedReminders = dayCompleted,
                missedReminders = missed,
                totalSpent = spent,
                screenTimeMinutes = screen,
                studyTimeMinutes = study
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderDao.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))
        }
    }
}
