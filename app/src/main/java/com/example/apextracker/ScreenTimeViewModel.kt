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
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val firebaseManager = FirebaseManager(application)

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _todayScreenTimeMillis = MutableStateFlow(0L)
    val todayScreenTimeMillis: StateFlow<Long> = _todayScreenTimeMillis.asStateFlow()

    private val _aggregatedUsage = MutableStateFlow<List<DeviceSession>>(emptyList())
    val aggregatedUsage: StateFlow<List<DeviceSession>> = _aggregatedUsage.asStateFlow()

    // Updated by the live cross-device listener (arbitrary cadence); recombined with the
    // freshly-measured self value on every 30s tick in refreshAggregatedUsage(). Each side
    // has exactly one writer, so there's no race — worst case is a few hundred ms staleness
    // on one side, never a dropped entry.
    private var latestOtherDevices: List<DeviceSession> = emptyList()

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
        viewModelScope.launch {
            if (firebaseManager.userId != null) {
                firebaseManager.getOtherDevicesScreenTimeFlow().collect { others ->
                    latestOtherDevices = others
                    refreshAggregatedUsage(_todayScreenTimeMillis.value)
                }
            }
        }
    }

    fun checkPermission() {
        val appOps = getApplication<Application>().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                getApplication<Application>().packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                getApplication<Application>().packageName
            )
        }
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
            // On API 30+ the <queries> LAUNCHER element in the manifest already limits what this
            // returns to launchable packages (Issue #72 — it replaced QUERY_ALL_PACKAGES). Below
            // API 30 package visibility doesn't exist and this returns every installed package,
            // so this filter is still what bounds the list on API 26-29. Keep it.
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
                safeCloudCall("ScreenTimeViewModel", "remove excluded app") {
                    firebaseManager.removeExcludedApp(app.packageName)
                }
            } else {
                excludedAppDao.excludeApp(ExcludedApp(app.packageName))
                safeCloudCall("ScreenTimeViewModel", "push excluded app") {
                    firebaseManager.pushExcludedApp(app.packageName)
                }
            }
            updateScreenTime()
        }
    }

    private fun startScreenTimeUpdates() {
        // 30s interval to avoid spamming Firestore
        viewModelScope.launchPeriodic(30_000) {
            if (_hasPermission.value) {
                updateScreenTime()
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

        // Upload to Firebase if logged in
        if (firebaseManager.userId != null) {
            firebaseManager.uploadScreenTimeSession(ScreenTimeSession(date = LocalDate.now(), durationMillis = totalFilteredTime))
        }
        refreshAggregatedUsage(totalFilteredTime)
    }

    private fun refreshAggregatedUsage(currentDeviceMillis: Long) {
        val currentDevice = DeviceSession(
            deviceId = firebaseManager.deviceId,
            deviceName = Build.MODEL,
            date = LocalDate.now().toString(),
            durationMillis = currentDeviceMillis,
            isCurrentDevice = true
        )
        _aggregatedUsage.value = listOf(currentDevice) + latestOtherDevices
    }

    // queryEvents is a blocking binder call and the event loop iterates the whole day's events;
    // this used to run on Main from the 30s poll and the installedApps combine (jank/ANR risk).
    private suspend fun calculateAppSpecificUsage(): Map<String, Long> = withContext(Dispatchers.IO) {
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

        val events = mutableListOf<ForegroundEvent>()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundEventKind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundEventKind.PAUSED
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                        event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                    ) {
                        ForegroundEventKind.SCREEN_OFF
                    } else {
                        null
                    }
                }
            }
            if (kind != null) {
                events.add(ForegroundEvent(kind, event.packageName ?: "", event.timeStamp))
            }
        }

        aggregateForegroundDurations(events, startTime, endTime)
    }

    private suspend fun saveTodayScreenTime(millis: Long) {
        val today = LocalDate.now()
        screenTimeDao.insertSession(ScreenTimeSession(date = today, durationMillis = millis))
    }

    fun getAllSessions() = screenTimeDao.getAllSessions()
}
