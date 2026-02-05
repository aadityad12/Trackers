package com.example.apextracker

import android.app.Application
import android.content.Context
import android.os.PowerManager
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
    private val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _timeSeconds = MutableStateFlow(0L)
    val timeSeconds: StateFlow<Long> = _timeSeconds.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var timerJob: Job? = null
    private var lastResetDate: LocalDate = LocalDate.now()
    
    // Accurate background timing variables
    private var lastStartTimeMillis: Long = 0L
    private var baseSeconds: Long = 0L

    init {
        loadTodaySession()
        startDailyResetCheck()
    }

    private fun loadTodaySession() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val session = studySessionDao.getSessionByDate(today)
            val savedSeconds = session?.durationSeconds ?: 0L
            _timeSeconds.value = savedSeconds
            baseSeconds = savedSeconds
        }
    }

    private fun startDailyResetCheck() {
        viewModelScope.launch {
            while (true) {
                val now = LocalDate.now()
                if (now.isAfter(lastResetDate)) {
                    // Save final time for previous day
                    val finalTotal = if (_isRunning.value) calculateCurrentTotalSeconds() else _timeSeconds.value
                    saveSessionForDate(lastResetDate, finalTotal)
                    
                    // Reset for new day
                    _timeSeconds.value = 0L
                    baseSeconds = 0L
                    if (_isRunning.value) {
                        lastStartTimeMillis = System.currentTimeMillis()
                    }
                    lastResetDate = now
                }
                delay(30000)
            }
        }
    }

    private fun calculateCurrentTotalSeconds(): Long {
        if (!_isRunning.value) return _timeSeconds.value
        val elapsedMillis = System.currentTimeMillis() - lastStartTimeMillis
        return baseSeconds + (elapsedMillis / 1000)
    }

    fun toggleTimer() {
        if (_isRunning.value) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        if (_isRunning.value) return
        
        lastStartTimeMillis = System.currentTimeMillis()
        baseSeconds = _timeSeconds.value
        _isRunning.value = true
        
        timerJob = viewModelScope.launch {
            while (_isRunning.value) {
                val current = calculateCurrentTotalSeconds()
                _timeSeconds.value = current
                saveSessionForDate(LocalDate.now(), current)
                delay(1000)
            }
        }
    }

    fun pauseTimer() {
        if (!_isRunning.value) return
        
        val total = calculateCurrentTotalSeconds()
        _timeSeconds.value = total
        baseSeconds = total
        _isRunning.value = false
        timerJob?.cancel()
        saveSessionForDate(LocalDate.now(), total)
    }

    fun resetTimerManual() {
        _isRunning.value = false
        timerJob?.cancel()
        _timeSeconds.value = 0L
        baseSeconds = 0L
        saveSessionForDate(LocalDate.now(), 0L)
    }

    private fun saveSessionForDate(date: LocalDate, duration: Long) {
        viewModelScope.launch {
            val session = StudySession(date = date, durationSeconds = duration)
            studySessionDao.insertSession(session)
        }
    }

    fun getAllSessions() = studySessionDao.getAllSessions()

    // Logic: Pause if user leaves app while screen is ON (Interactive).
    // Keep counting if screen turns OFF (Non-Interactive).
    fun handleAppBackground() {
        if (_isRunning.value && powerManager.isInteractive) {
            pauseTimer()
        }
    }
}
