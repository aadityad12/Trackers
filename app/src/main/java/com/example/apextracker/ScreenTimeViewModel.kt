package com.example.apextracker

import android.app.Application
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isExcluded: Boolean
)

class ScreenTimeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val screenTimeDao = database.screenTimeSessionDao()
    private val excludedAppDao = database.excludedAppDao()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _todayScreenTimeMillis = MutableStateFlow(0L)
    val todayScreenTimeMillis: StateFlow<Long> = _todayScreenTimeMillis.asStateFlow()

    private val _excludedApps = excludedAppDao.getExcludedApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _installedApps = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val installedApps: StateFlow<List<AppUsageInfo>> = combine(_installedApps, _excludedApps) { installed, excluded ->
        installed.map { app ->
            app.copy(isExcluded = excluded.any { it.packageName == app.packageName })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkPermission()
        loadInstalledApps()
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

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            // Use MATCH_ALL to find all installed applications
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app ->
                    // Filter for apps that the user actually interacts with:
                    // 1. It's not a system app OR
                    // 2. It's a pre-installed app that has a launcher entry (like Chrome, YouTube, etc.)
                    val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val hasLauncher = pm.getLaunchIntentForPackage(app.packageName) != null
                    !isSystemApp || hasLauncher
                }
                .map { app ->
                    AppUsageInfo(
                        packageName = app.packageName,
                        appName = pm.getApplicationLabel(app).toString(),
                        icon = try { pm.getApplicationIcon(app) } catch (e: Exception) { null },
                        isExcluded = false
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName }
            _installedApps.value = apps
        }
    }

    fun toggleAppExclusion(app: AppUsageInfo) {
        viewModelScope.launch {
            if (app.isExcluded) {
                excludedAppDao.includeApp(ExcludedApp(app.packageName))
            } else {
                excludedAppDao.excludeApp(ExcludedApp(app.packageName))
            }
            // Trigger recalculation immediately
            updateScreenTime()
        }
    }

    private fun startScreenTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                if (_hasPermission.value) {
                    updateScreenTime()
                }
                delay(30000) // Update every 30 seconds
            }
        }
    }

    private suspend fun updateScreenTime() {
        val totalTime = calculateTodayScreenTime()
        _todayScreenTimeMillis.value = totalTime
        saveTodayScreenTime(totalTime)
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

        val excludedPackageNames = _excludedApps.value.map { it.packageName }.toSet()

        // Filter out excluded apps AND the tracker app itself if desired
        return stats?.filter { it.packageName !in excludedPackageNames && it.packageName != getApplication<Application>().packageName }
            ?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    private suspend fun saveTodayScreenTime(millis: Long) {
        val today = LocalDate.now()
        screenTimeDao.insertSession(ScreenTimeSession(date = today, durationMillis = millis))
    }

    fun getAllSessions() = screenTimeDao.getAllSessions()
}
