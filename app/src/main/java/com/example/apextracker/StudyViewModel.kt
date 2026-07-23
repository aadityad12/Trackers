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
import java.time.ZoneId

class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "StudyViewModel"
        const val CLOUD_PUSH_INTERVAL_MILLIS = 60_000L
    }

    private val database = AppDatabase.getDatabase(application)
    private val studySessionDao = database.studySessionDao()
    private val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val firebaseManager = FirebaseManager(application)
    private val timerStateStore = StudyTimerStateStore(application)
    private var lastCloudPushMillis = 0L

    private val _timeSeconds = MutableStateFlow(0L)
    val timeSeconds: StateFlow<Long> = _timeSeconds.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // The subject the stopwatch is currently attributing time to. "" is the "No subject" bucket
    // and the startup default, so a user who never picks a subject behaves exactly as pre-#78.
    private val _currentSubject = MutableStateFlow("")
    val currentSubject: StateFlow<String> = _currentSubject.asStateFlow()

    private var timerJob: Job? = null
    private var lastResetDate: LocalDate = LocalDate.now()
    
    // Accurate background timing variables
    private var lastStartTimeMillis: Long = 0L
    private var baseSeconds: Long = 0L

    init {
        restoreSession()
        startDailyResetCheck()
    }

    /**
     * Loads today's saved total — and if the process was killed while the timer ran, restores
     * the stopwatch instead of silently dropping it: a same-day death resumes counting from the
     * persisted start time; a death on an earlier day credits that day up to its midnight
     * boundary (time past midnight isn't credited — we can't know when the process died) and
     * leaves the timer stopped.
     */
    private fun restoreSession() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val persisted = timerStateStore.loadRunning()
            if (persisted != null && persisted.date == today) {
                _currentSubject.value = persisted.subject
                baseSeconds = persisted.baseSeconds
                lastStartTimeMillis = persisted.startedAtMillis
                lastResetDate = today
                _isRunning.value = true
                _timeSeconds.value = calculateCurrentTotalSeconds()
                launchTicker()
                return@launch
            }
            if (persisted != null) {
                val finalSeconds = finalizeSecondsAtEndOfDay(persisted, ZoneId.systemDefault())
                saveSessionForDate(persisted.date, persisted.subject, finalSeconds, forcePush = true)
                timerStateStore.clear()
            }
            // The displayed total is the current subject's total for today ("" at startup).
            val savedSeconds = studySessionDao.getSession(today, _currentSubject.value)?.durationSeconds ?: 0L
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
        saveSessionForDate(lastResetDate, _currentSubject.value, finalTotal, forcePush = true)

        // Reset for new day
        _timeSeconds.value = 0L
        baseSeconds = 0L
        if (_isRunning.value) {
            lastStartTimeMillis = System.currentTimeMillis()
        }
        lastResetDate = now
        if (_isRunning.value) {
            timerStateStore.saveRunning(lastStartTimeMillis, baseSeconds, lastResetDate, _currentSubject.value)
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

        rolloverIfNeeded()
        lastStartTimeMillis = System.currentTimeMillis()
        baseSeconds = _timeSeconds.value
        _isRunning.value = true
        timerStateStore.saveRunning(lastStartTimeMillis, baseSeconds, lastResetDate, _currentSubject.value)
        launchTicker()
    }

    /**
     * Switches the subject the stopwatch attributes time to. If the timer is running it's paused
     * (banking the elapsed time under the *old* subject) and resumed under the new one, so switching
     * mid-study never misattributes seconds. The displayed total swaps to the new subject's own
     * accumulated total for today — the stopwatch shows "this subject today", not the daily grand
     * total. A no-op when the normalised subject is unchanged.
     */
    fun selectSubject(subject: String) {
        val normalized = normalizeSubject(subject)
        if (normalized == _currentSubject.value) return

        val wasRunning = _isRunning.value
        if (wasRunning) pauseTimer()
        _currentSubject.value = normalized
        viewModelScope.launch {
            rolloverIfNeeded()
            val saved = studySessionDao.getSession(lastResetDate, normalized)?.durationSeconds ?: 0L
            _timeSeconds.value = saved
            baseSeconds = saved
            if (wasRunning) startTimer()
        }
    }

    private fun launchTicker() {
        timerJob = viewModelScope.launch {
            while (_isRunning.value) {
                rolloverIfNeeded()
                val current = calculateCurrentTotalSeconds()
                _timeSeconds.value = current
                // Attribute to the tracked day and current subject, not LocalDate.now():
                // rolloverIfNeeded just synchronized lastResetDate, and selectSubject pauses the
                // ticker before switching, so this can never write into the wrong row.
                saveSessionForDate(lastResetDate, _currentSubject.value, current)
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
        timerStateStore.clear()
        saveSessionForDate(lastResetDate, _currentSubject.value, total, forcePush = true)
    }

    /** Resets today's total for the currently selected subject only; other subjects are untouched. */
    fun resetTimerManual() {
        rolloverIfNeeded()
        _isRunning.value = false
        timerJob?.cancel()
        timerStateStore.clear()
        _timeSeconds.value = 0L
        baseSeconds = 0L
        saveSessionForDate(lastResetDate, _currentSubject.value, 0L, forcePush = true)
    }

    private fun saveSessionForDate(date: LocalDate, subject: String, duration: Long, forcePush: Boolean = false) {
        viewModelScope.launch {
            val session = StudySession(date = date, subject = subject, durationSeconds = duration)
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

    /**
     * Records (or overwrites) the total for a past [date] and [subject] — the manual backfill path
     * for a session the timer never saw (Issue #122). Today is deliberately excluded: the running
     * timer owns today's row and would overwrite anything written here on its next tick.
     * [durationSeconds] of 0 clears that day's entry for the subject.
     */
    fun logManualSession(date: LocalDate, subject: String, durationSeconds: Long): Boolean {
        if (!date.isBefore(LocalDate.now()) || durationSeconds < 0) return false
        saveSessionForDate(date, normalizeSubject(subject), durationSeconds, forcePush = true)
        return true
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
