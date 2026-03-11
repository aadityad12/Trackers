package com.example.apextracker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
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

    LifecycleEffect(onEvent = { viewModel.checkPermission() })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screen Time") },
                navigationIcon = {
                    IconButton(onClick = if (showSettings) { { showSettings = false } } else onBackToMenu) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasPermission && !showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Exclude Apps")
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
                                "Device Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
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
                            "Today's Apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val activeApps = apps.filter { it.usageTimeMillis > 0 && !it.isExcluded }
                    if (activeApps.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No app usage detected", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        items(activeApps.take(5)) { app ->
                            AppUsageItem(app)
                        }
                    }

                    item {
                        Text(
                            "Daily History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val history = allSessions.filter { it.date.isBefore(LocalDate.now()) }
                    if (history.isEmpty()) {
                        item {
                            Text("No history recorded yet", color = MaterialTheme.colorScheme.outline)
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
fun TotalApexTimeCard(devices: List<DeviceUsage>) {
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
                text = "Total Apex Time",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = formatMillis(totalMillis),
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
                        text = "${devices.size} Connected Devices",
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
fun DeviceBreakdownItem(usage: DeviceUsage) {
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
                text = formatMillis(usage.durationMillis),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AppUsageItem(app: AppUsageInfo) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
        Text(app.appName, modifier = Modifier.weight(1f), maxLines = 1)
        Text(formatMillis(app.usageTimeMillis), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ExcludeAppsList(apps: List<AppUsageInfo>, onToggle: (AppUsageInfo) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Tracking Preferences",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Toggle off apps you don't want to include in your total screen time.",
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
                    text = if (app.isExcluded) "Excluded" else "Tracking",
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
                "Permission Required",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Apex Tracker needs 'Usage Access' permission to track your screen time. This information is only stored on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGrantClick) {
                Text("Grant Permission")
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
                text = formatMillis(session.durationMillis),
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

fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%dh %dm", hours, minutes)
    } else {
        String.format(Locale.getDefault(), "%dm", minutes)
    }
}
