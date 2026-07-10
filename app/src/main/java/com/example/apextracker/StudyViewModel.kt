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
    private companion object {
        const val TAG = "StudyViewModel"
        const val CLOUD_PUSH_INTERVAL_MILLIS = 60_000L
    }

    private val database = AppDatabase.getDatabase(application)
    private val studySessionDao = database.studySessionDao()
    private val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val firebaseManager = FirebaseManager(application)
    private var lastCloudPushMillis = 0L

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
        // While the timer runs, the per-second ticker rolls over itself; this poll only
        // covers the idle case (zeroing the displayed counter at midnight).
        viewModelScope.launchPeriodic(30_000) {
            rolloverIfNeeded()
        }
    }

    /**
     * If the calendar day advanced past [lastResetDate], saves that day's final total to its own
     * row and resets the counter for the new day. Called from every path that writes a session
     * (ticker, pause, reset) so a running total can never be attributed to the wrong day —
     * previously only a 30s poll did this, and until it fired the per-second loop wrote
     * yesterday's entire running total into the new day's row.
     */
    private fun rolloverIfNeeded() {
        val now = LocalDate.now()
        if (!now.isAfter(lastResetDate)) return

        // Force the cloud push: it's the last chance to write that date's document
        val finalTotal = if (_isRunning.value) calculateCurrentTotalSeconds() else _timeSeconds.value
        saveSessionForDate(lastResetDate, finalTotal, forcePush = true)

        // Reset for new day
        _timeSeconds.value = 0L
        baseSeconds = 0L
        if (_isRunning.value) {
            lastStartTimeMillis = System.currentTimeMillis()
        }
        lastResetDate = now
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
                rolloverIfNeeded()
                val current = calculateCurrentTotalSeconds()
                _timeSeconds.value = current
                // Attribute to the tracked day, not LocalDate.now(): rolloverIfNeeded just
                // synchronized lastResetDate, so this can never write into the wrong row.
                saveSessionForDate(lastResetDate, current)
                delay(1000)
            }
        }
    }

    fun pauseTimer() {
        if (!_isRunning.value) return

        rolloverIfNeeded()
        val total = calculateCurrentTotalSeconds()
        _timeSeconds.value = total
        baseSeconds = total
        _isRunning.value = false
        timerJob?.cancel()
        saveSessionForDate(lastResetDate, total, forcePush = true)
    }

    fun resetTimerManual() {
        rolloverIfNeeded()
        _isRunning.value = false
        timerJob?.cancel()
        _timeSeconds.value = 0L
        baseSeconds = 0L
        saveSessionForDate(lastResetDate, 0L, forcePush = true)
    }

    private fun saveSessionForDate(date: LocalDate, duration: Long, forcePush: Boolean = false) {
        viewModelScope.launch {
            val session = StudySession(date = date, durationSeconds = duration)
            studySessionDao.insertSession(session)
            // Room saves every second while running; the cloud push is throttled to a
            // 60s heartbeat, with significant events (pause/reset/rollover) forced.
            val now = System.currentTimeMillis()
            if (shouldSyncNow(now, lastCloudPushMillis, forcePush, CLOUD_PUSH_INTERVAL_MILLIS)) {
                lastCloudPushMillis = now
                safeCloudCall(TAG, "push study session") {
                    firebaseManager.pushStudySession(session)
                }
            }
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
