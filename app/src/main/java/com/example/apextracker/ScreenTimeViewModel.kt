package com.example.apextracker

import android.app.Application
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class ScreenTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val screenTimeDao = database.screenTimeSessionDao()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _todayScreenTimeMillis = MutableStateFlow(0L)
    val todayScreenTimeMillis: StateFlow<Long> = _todayScreenTimeMillis.asStateFlow()

    init {
        checkPermission()
        startScreenTimeUpdates()
    }

    fun checkPermission() {
        val appOps = getApplication<Application>().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            getApplication<Application>().packageName
        )
        _hasPermission.value = mode == AppOpsManager.MODE_ALLOWED
    }

    fun openPermissionSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    private fun startScreenTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                if (_hasPermission.value) {
                    val totalTime = calculateTodayScreenTime()
                    _todayScreenTimeMillis.value = totalTime
                    saveTodayScreenTime(totalTime)
                }
                delay(30000) // Update every 30 seconds
            }
        }
    }

    private fun calculateTodayScreenTime(): Long {
        val usageStatsManager = getApplication<Application>().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        return stats?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    private suspend fun saveTodayScreenTime(millis: Long) {
        val today = LocalDate.now()
        screenTimeDao.insertSession(ScreenTimeSession(date = today, durationMillis = millis))
    }

    fun getAllSessions() = screenTimeDao.getAllSessions()
}
