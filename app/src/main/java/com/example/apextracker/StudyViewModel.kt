package com.example.apextracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val studySessionDao = database.studySessionDao()

    private val _timeSeconds = MutableStateFlow(0L)
    val timeSeconds: StateFlow<Long> = _timeSeconds.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var timerJob: Job? = null
    private var lastResetDate: LocalDate = LocalDate.now()

    init {
        loadTodaySession()
        startDailyResetCheck()
    }

    private fun loadTodaySession() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val session = studySessionDao.getSessionByDate(today)
            _timeSeconds.value = session?.durationSeconds ?: 0L
        }
    }

    private fun startDailyResetCheck() {
        viewModelScope.launch {
            while (true) {
                val now = LocalDate.now()
                if (now.isAfter(lastResetDate)) {
                    // Date changed while app was open (even if paused)
                    // Save whatever was there to the previous date and reset
                    saveSessionForDate(lastResetDate, _timeSeconds.value)
                    _timeSeconds.value = 0L
                    _isRunning.value = false
                    timerJob?.cancel()
                    lastResetDate = now
                }
                delay(30000) // Check every 30 seconds
            }
        }
    }

    fun toggleTimer() {
        if (_isRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        _isRunning.value = true
        timerJob = viewModelScope.launch {
            var currentDate = LocalDate.now()
            while (_isRunning.value) {
                delay(1000)
                val now = LocalDate.now()
                
                if (now.isAfter(currentDate)) {
                    // Date changed during active tracking
                    saveSessionForDate(currentDate, _timeSeconds.value)
                    _timeSeconds.value = 0L
                    currentDate = now
                    lastResetDate = now
                }
                
                _timeSeconds.value++
                saveSessionForDate(currentDate, _timeSeconds.value)
            }
        }
    }

    private fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()
        saveSessionForDate(LocalDate.now(), _timeSeconds.value)
    }

    fun resetTimerManual() {
        _isRunning.value = false
        timerJob?.cancel()
        // Save 0 to today's date to reset the stored total
        saveSessionForDate(LocalDate.now(), 0L)
        _timeSeconds.value = 0L
    }

    private fun saveSessionForDate(date: LocalDate, duration: Long) {
        viewModelScope.launch {
            val session = StudySession(date = date, durationSeconds = duration)
            studySessionDao.insertSession(session)
        }
    }

    fun getAllSessions() = studySessionDao.getAllSessions()
}
