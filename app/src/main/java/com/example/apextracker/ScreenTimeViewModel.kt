package com.example.apextracker

import android.app.Application
import android.app.AppOpsManager
import android.app.usage.UsageEvents
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
    val isExcluded: Boolean,
    val usageTimeMillis: Long = 0L
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
    val installedApps: StateFlow<List<AppUsageInfo>> = combine(_installedApps, _excludedApps, _todayScreenTimeMillis) { installed, excluded, _ ->
        val currentStats = calculateAppSpecificUsage()
        installed.map { app ->
            app.copy(
                isExcluded = excluded.any { it.packageName == app.packageName },
                usageTimeMillis = currentStats[app.packageName] ?: 0L
            )
        }.sortedByDescending { it.usageTimeMillis }
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
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app ->
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
            updateScreenTime()
        }
    }

    private fun startScreenTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                if (_hasPermission.value) {
                    updateScreenTime()
                }
                delay(10000)
            }
        }
    }

    private suspend fun updateScreenTime() {
        val usageMap = calculateAppSpecificUsage()
        val excludedPackageNames = _excludedApps.value.map { it.packageName }.toSet()
        val myPackageName = getApplication<Application>().packageName
        
        val launcherPackage = try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = getApplication<Application>().packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) { null }

        val totalFilteredTime = usageMap.filter { (pkg, _) ->
            pkg !in excludedPackageNames && 
            pkg != myPackageName &&
            pkg != launcherPackage &&
            pkg != "com.android.systemui"
        }.values.sum()

        _todayScreenTimeMillis.value = totalFilteredTime
        saveTodayScreenTime(totalFilteredTime)
    }

    private fun calculateAppSpecificUsage(): Map<String, Long> {
        val usageStatsManager = getApplication<Application>().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        
        val appUsageMap = mutableMapOf<String, Long>()
        val lastEventTime = mutableMapOf<String, Long>()
        var currentForegroundApp: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // App came to foreground
                    currentForegroundApp = event.packageName
                    lastEventTime[event.packageName] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    // App went to background
                    val startTimeForApp = lastEventTime[event.packageName]
                    if (startTimeForApp != null) {
                        val duration = event.timeStamp - startTimeForApp
                        appUsageMap[event.packageName] = (appUsageMap[event.packageName] ?: 0L) + duration
                        lastEventTime.remove(event.packageName)
                    }
                    if (currentForegroundApp == event.packageName) {
                        currentForegroundApp = null
                    }
                }
            }
        }

        // Add duration for the app currently in foreground
        currentForegroundApp?.let { pkg ->
            val startTimeForApp = lastEventTime[pkg]
            if (startTimeForApp != null) {
                val duration = endTime - startTimeForApp
                appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + duration
            }
        }

        return appUsageMap
    }

    private suspend fun saveTodayScreenTime(millis: Long) {
        val today = LocalDate.now()
        screenTimeDao.insertSession(ScreenTimeSession(date = today, durationMillis = millis))
    }

    fun getAllSessions() = screenTimeDao.getAllSessions()
}
