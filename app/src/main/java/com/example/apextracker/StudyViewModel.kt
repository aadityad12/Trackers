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
import java.time.LocalDateTime
import java.time.LocalTime

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
                    resetTimerAuto()
                    lastResetDate = now
                }
                delay(60000) // Check every minute
            }
        }
    }

    private fun resetTimerAuto() {
        _isRunning.value = false
        timerJob?.cancel()
        _timeSeconds.value = 0L
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
            while (_isRunning.value) {
                delay(1000)
                _timeSeconds.value++
                saveSession()
            }
        }
    }

    private fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()
        saveSession()
    }

    fun resetTimerManual() {
        _isRunning.value = false
        timerJob?.cancel()
        _timeSeconds.value = 0L
        saveSession()
    }

    private fun saveSession() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val session = StudySession(date = today, durationSeconds = _timeSeconds.value)
            studySessionDao.insertSession(session)
        }
    }

    fun getAllSessions() = studySessionDao.getAllSessions()
}
