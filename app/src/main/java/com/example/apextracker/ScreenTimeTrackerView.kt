package com.example.apextracker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTimeTrackerView(onBackToMenu: () -> Unit, viewModel: ScreenTimeViewModel = viewModel()) {
    val hasPermission by viewModel.hasPermission.collectAsState()
    val todayMillis by viewModel.todayScreenTimeMillis.collectAsState()
    val allSessions by viewModel.getAllSessions().collectAsState(initial = emptyList())
    val apps by viewModel.installedApps.collectAsState()
    val aggregatedUsage by viewModel.aggregatedUsage.collectAsState()
    
    var showSettings by remember { mutableStateOf(false) }
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    // Non-null while the per-app limit dialog is open (Issue #124).
    var appForLimit by remember { mutableStateOf<AppUsageInfo?>(null) }

    appForLimit?.let { app ->
        AppLimitDialog(
            app = app,
            onDismiss = { appForLimit = null },
            onSave = { minutes ->
                viewModel.setAppLimit(app, minutes)
                appForLimit = null
            }
        )
    }

    LifecycleEffect(onEvent = { viewModel.checkPermission() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_time_title)) },
                navigationIcon = {
                    IconButton(onClick = if (showSettings) { { showSettings = false } } else onBackToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (hasPermission && !showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_exclude_apps))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (!hasPermission) {
                PermissionRequestCard(onGrantClick = { viewModel.openPermissionSettings() })
            } else if (showSettings) {
                ExcludeAppsList(apps, onToggle = { viewModel.toggleAppExclusion(it) })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        TotalApexTimeCard(aggregatedUsage)
                    }

                    if (aggregatedUsage.size > 1) {
                        item {
                            Text(
                                stringResource(R.string.screen_device_breakdown),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                aggregatedUsage.forEach { usage ->
                                    DeviceBreakdownItem(usage)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            stringResource(R.string.screen_todays_apps),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    // apps is already sorted by usage descending in the ViewModel. Show the top 5
                    // by default with a "Show all (N)" toggle revealing every non-excluded app that
                    // logged usage today. Rendering more rows is purely presentational — it reads
                    // precomputed state and never re-triggers usage calculation.
                    val activeApps = apps.filter { it.usageTimeMillis > 0 && !it.isExcluded }
                    if (activeApps.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.screen_no_usage), color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        val visibleApps = if (showAllApps) activeApps else activeApps.take(5)
                        items(visibleApps, key = { it.packageName }) { app ->
                            AppUsageItem(app, onClick = { appForLimit = app })
                        }
                        if (activeApps.size > 5) {
                            item {
                                TextButton(onClick = { showAllApps = !showAllApps }) {
                                    Text(
                                        if (showAllApps) stringResource(R.string.screen_show_less)
                                        else stringResource(R.string.screen_show_all, activeApps.size)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            stringResource(R.string.screen_daily_history),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    item {
                        ScreenTimeTrendsCard(allSessions)
                    }

                    val history = allSessions.filter { it.date.isBefore(LocalDate.now()) }
                    if (history.isEmpty()) {
                        item {
                            Text(stringResource(R.string.screen_no_history), color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        items(history.take(7)) { session ->
                            ScreenTimeHistoryItem(session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TotalApexTimeCard(devices: List<DeviceSession>) {
    val totalMillis = devices.sumOf { it.durationMillis }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.screen_total_apex_time),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = formatDurationCompact(totalMillis),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (devices.size > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.screen_connected_devices, devices.size),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceBreakdownItem(usage: DeviceSession) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (usage.deviceName.contains("Phone", true)) Icons.Default.Smartphone else Icons.Default.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = usage.deviceName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatDurationCompact(usage.durationMillis),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AppUsageItem(app: AppUsageInfo, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        app.icon?.let {
            Image(
                bitmap = it.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appName, maxLines = 1)
            app.limitMinutes?.let { minutes ->
                // Limit badge (Issue #124): "Limit: 30m" normally, "Over limit · 30m" in error red.
                Text(
                    text = if (app.isOverLimit) {
                        stringResource(R.string.screen_limit_over, minutes)
                    } else {
                        stringResource(R.string.screen_limit_set, minutes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            formatDurationCompact(app.usageTimeMillis),
            fontWeight = FontWeight.Bold,
            color = if (app.isOverLimit) MaterialTheme.colorScheme.error else Color.Unspecified
        )
    }
}

/** Set or clear a per-app daily screen-time limit (Issue #124). */
@Composable
fun AppLimitDialog(app: AppUsageInfo, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var text by remember { mutableStateOf(app.limitMinutes?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screen_limit_dialog_title, app.appName)) },
        text = {
            Column {
                Text(stringResource(R.string.screen_limit_dialog_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text(stringResource(R.string.screen_limit_minutes_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text.toIntOrNull()) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (app.limitMinutes != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.screen_limit_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

@Composable
fun ExcludeAppsList(apps: List<AppUsageInfo>, onToggle: (AppUsageInfo) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.screen_tracking_prefs),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.screen_tracking_prefs_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(apps) { app ->
                AppToggleItem(app, onToggle)
            }
        }
    }
}

@Composable
fun AppToggleItem(app: AppUsageInfo, onToggle: (AppUsageInfo) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isExcluded) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            app.icon?.let {
                Image(
                    bitmap = it.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, fontWeight = FontWeight.Medium)
                Text(
                    text = stringResource(if (app.isExcluded) R.string.screen_app_excluded else R.string.screen_app_tracking),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Switch(
                checked = !app.isExcluded,
                onCheckedChange = { onToggle(app) }
            )
        }
    }
}

@Composable
fun PermissionRequestCard(onGrantClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.screen_permission_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.screen_permission_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGrantClick) {
                Text(stringResource(R.string.screen_grant_permission))
            }
        }
    }
}

@Composable
fun ScreenTimeHistoryItem(session: ScreenTimeSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = session.date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = formatDurationCompact(session.durationMillis),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LifecycleEffect(onEvent: () -> Unit) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onEvent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

