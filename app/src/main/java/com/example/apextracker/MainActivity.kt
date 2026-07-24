package com.example.apextracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.apextracker.ui.theme.ApexTheme
import com.example.apextracker.ui.theme.ApexTrackerTheme
import com.example.apextracker.ui.theme.EmeraldMuted
import com.example.apextracker.ui.theme.MagmaPrimary
import com.example.apextracker.ui.theme.OceanPrimary
import com.example.apextracker.ui.theme.RoyalPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// FragmentActivity (not ComponentActivity) because androidx.biometric's BiometricPrompt requires
// one to host its prompt (Issue #45). FragmentActivity is itself a ComponentActivity, so setContent,
// viewModel(), and registerForActivityResult all keep working unchanged.
class MainActivity : FragmentActivity() {
    companion object {
        /** Intent extra naming a navigation route to open (e.g. from a notification tap). */
        const val EXTRA_NAVIGATE_TO = "navigate_to"

        // Process-scoped so activity recreation (rotation, theme change) doesn't
        // re-trigger the cold-start initial sync. See shouldRunInitialSync().
        private var initialSyncRanThisProcess = false
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* If denied, reminder notifications simply won't post; nothing else to do here. */ }

    // Route requested by the launching intent; consumed (nulled) once navigated.
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute = sanitizeRequestedRoute(intent.getStringExtra(EXTRA_NAVIGATE_TO))
    }

    override fun onStop() {
        super.onStop()
        // Re-lock every module when the app leaves the foreground, so returning re-prompts (the
        // unlocked-until-backgrounded policy, Issue #45). onStop also covers the system biometric
        // prompt bringing its own UI forward, but markUnlocked() runs on its success callback
        // afterwards, so a genuine unlock still sticks for the session.
        UnlockSession.lockAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute = sanitizeRequestedRoute(intent?.getStringExtra(EXTRA_NAVIGATE_TO))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val user by authViewModel.user.collectAsState()
            
            var currentTheme by rememberSaveable { mutableStateOf(ApexTheme.EMERALD) }
            var isDarkMode by rememberSaveable { mutableStateOf(true) }

            // Currency lives in DataStore rather than rememberSaveable — unlike theme, it must
            // survive a restart for signed-out users too (Issue #76).
            val currencySettings = remember { CurrencySettings(this) }
            val storedCurrency by currencySettings.currencyCode.collectAsState(initial = null)
            val scope = rememberCoroutineScope()

            // Sync settings when they change if logged in
            val firebaseManager = remember { FirebaseManager(this) }
            var previousUser by remember { mutableStateOf(firebaseManager.currentUser) }

            // Trigger full initial sync on new sign-in AND once per process when a
            // signed-in session was restored on cold start (Issue #17 — the transition
            // check alone never fires for returning users, so cross-device changes and
            // post-destructive-migration restores never arrived).
            LaunchedEffect(user) {
                if (shouldRunInitialSync(
                        signedIn = user != null,
                        wasSignedOut = previousUser == null,
                        alreadyRanThisProcess = initialSyncRanThisProcess
                    )
                ) {
                    initialSyncRanThisProcess = true
                    authViewModel.setSyncing(true)
                    try {
                        firebaseManager.performInitialSync(AppDatabase.getDatabase(applicationContext))
                    } finally {
                        authViewModel.setSyncing(false)
                    }
                }
                if (user != null) {
                    SyncCoordinator.start(firebaseManager, AppDatabase.getDatabase(applicationContext))
                } else {
                    SyncCoordinator.stop()
                }
                previousUser = user
            }

            // Gate on storedCurrency != null: pushing before DataStore has read back would send the
            // locale default and clobber the real stored value on every cold start.
            LaunchedEffect(currentTheme, isDarkMode, storedCurrency, user) {
                if (user != null && storedCurrency != null) {
                    firebaseManager.syncSettings(currentTheme.name, isDarkMode, storedCurrency)
                }
            }

            // Load remote settings
            LaunchedEffect(user) {
                if (user != null) {
                    firebaseManager.getSettingsFlow().collect { settings ->
                        settings?.let {
                            (it["theme"] as? String)?.let { themeName ->
                                try { currentTheme = ApexTheme.valueOf(themeName) } catch (e: Exception) {}
                            }
                            (it["isDarkMode"] as? Boolean)?.let { dark ->
                                isDarkMode = dark
                            }
                            // Another client wrote this; validate before persisting so a bad code
                            // can't poison local prefs. Writing an identical value back is a no-op
                            // for DataStore, so this can't ping-pong with the push effect above.
                            parseCurrencySafe(it["currency"] as? String)?.let { currency ->
                                currencySettings.setCurrencyCode(currency.currencyCode)
                            }
                        }
                    }
                }
            }

            ApexTrackerTheme(theme = currentTheme, darkTheme = isDarkMode) {
                CompositionLocalProvider(LocalCurrencyCode provides (storedCurrency ?: defaultCurrencyCode())) {
                    AppNavigation(
                        currentTheme = currentTheme,
                        isDarkMode = isDarkMode,
                        onThemeChange = { currentTheme = it },
                        onDarkModeChange = { isDarkMode = it },
                        currencyCode = storedCurrency ?: defaultCurrencyCode(),
                        onCurrencyChange = { scope.launch { currencySettings.setCurrencyCode(it) } },
                        requestedRoute = pendingRoute,
                        onRequestedRouteConsumed = { pendingRoute = null }
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    currentTheme: ApexTheme,
    isDarkMode: Boolean,
    onThemeChange: (ApexTheme) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    currencyCode: String,
    onCurrencyChange: (String) -> Unit,
    requestedRoute: String? = null,
    onRequestedRouteConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    var showSplash by remember { mutableStateOf(true) }

    // Per-module lock flags (Issue #45). initial = null so the gate fails closed until DataStore
    // has read the real value — see LockGate.
    val appContext = LocalContext.current
    val securitySettings = remember { SecuritySettings(appContext) }
    val budgetLockEnabled by securitySettings.budgetLockEnabled.collectAsState(initial = null)
    val notesLockEnabled by securitySettings.notesLockEnabled.collectAsState(initial = null)

    // Auth/settings state for the app-wide settings sheet + sync indicator, hosted here now that the
    // Dashboard (not a MainMenu) is the home surface. This AuthViewModel drives sign-in/out from the
    // settings sheet and the dashboard's sync icon; MainActivity keeps its own instance for sync
    // orchestration, same as MainMenu used to.
    val authViewModel: AuthViewModel = viewModel()
    val user by authViewModel.user.collectAsState()
    val isSyncing by authViewModel.isSyncing.collectAsState()
    val signInError by authViewModel.signInError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(signInError) {
        val error = signInError
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            authViewModel.clearSignInError()
        }
    }
    var showSettings by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
    } else {
        // Honor a route requested by the launching intent (notification tap) once the
        // NavHost exists — covers both cold start and onNewIntent while running.
        LaunchedEffect(requestedRoute) {
            if (requestedRoute != null) {
                navController.navigate(requestedRoute) { launchSingleTop = true }
                onRequestedRouteConsumed()
            }
        }

        val enterAnimSpec = tween<Float>(durationMillis = 400, easing = FastOutSlowInEasing)
        val exitAnimSpec = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)

        // Bottom bar shows only on the primary destinations; secondary screens (goals, reminders,
        // notes, overview) hide it and rely on their own back arrow.
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (currentRoute in PRIMARY_ROUTES) {
                    AppBottomBar(
                        currentRoute = currentRoute,
                        onSelectPrimary = { route ->
                            navController.navigate(route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onMore = { showMoreSheet = true }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = enterAnimSpec) +
                        scaleIn(initialScale = 0.94f, animationSpec = enterAnimSpec)
            },
            exitTransition = {
                fadeOut(animationSpec = exitAnimSpec) +
                        scaleOut(targetScale = 1.06f, animationSpec = exitAnimSpec)
            },
            popEnterTransition = {
                fadeIn(animationSpec = enterAnimSpec) +
                        scaleIn(initialScale = 1.06f, animationSpec = enterAnimSpec)
            },
            popExitTransition = {
                fadeOut(animationSpec = exitAnimSpec) +
                        scaleOut(targetScale = 0.94f, animationSpec = exitAnimSpec)
            }
        ) {
            composable("overview") {
                OverviewView(
                    onBackToMenu = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("dashboard") {
                DashboardView(
                    onManageGoals = { navController.navigate("goals") },
                    onOpenSettings = { showSettings = true },
                    signedIn = user != null,
                    isSyncing = isSyncing
                )
            }
            composable("goals") {
                GoalsView(onBack = { navController.popBackStack() })
            }
            composable("budget_tracker") {
                LockGate(
                    route = "budget_tracker",
                    lockEnabled = budgetLockEnabled,
                    promptTitle = stringResource(R.string.security_prompt_title, stringResource(R.string.module_budget)),
                    promptSubtitle = stringResource(R.string.security_lock_subtitle),
                    onCancelled = { navController.popBackStack() }
                ) {
                    BudgetTrackerApp(onBackToMenu = { navController.popBackStack() })
                }
            }
            composable("study_tracker") {
                StudyTrackerView(onBackToMenu = { navController.popBackStack() })
            }
            composable("screen_time") {
                ScreenTimeTrackerView(onBackToMenu = { navController.popBackStack() })
            }
            composable("reminders") {
                ReminderView(onBackToMenu = { navController.popBackStack() })
            }
            composable("notes") {
                LockGate(
                    route = "notes",
                    lockEnabled = notesLockEnabled,
                    promptTitle = stringResource(R.string.security_prompt_title, stringResource(R.string.module_notes)),
                    promptSubtitle = stringResource(R.string.security_lock_subtitle),
                    onCancelled = { navController.popBackStack() }
                ) {
                    NoteView(onBackToMenu = { navController.popBackStack() })
                }
            }
        }
        } // Scaffold content

        if (showSettings) {
            AppSettingsSheet(
                onDismiss = { showSettings = false },
                currentTheme = currentTheme,
                isDarkMode = isDarkMode,
                onThemeChange = onThemeChange,
                onDarkModeChange = onDarkModeChange,
                currencyCode = currencyCode,
                onCurrencyChange = onCurrencyChange,
                authViewModel = authViewModel
            )
        }
        if (showMoreSheet) {
            MoreSheet(
                onDismiss = { showMoreSheet = false },
                onSelect = { route ->
                    showMoreSheet = false
                    // Reset to the dashboard root first, so backing out of a More destination
                    // always lands on the home surface rather than whatever tab was showing.
                    navController.navigate(route) {
                        popUpTo("dashboard")
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

private val PRIMARY_ROUTES = setOf("dashboard", "study_tracker", "screen_time", "budget_tracker")

private data class BottomDest(val route: String, val icon: ImageVector, @StringRes val label: Int)

/**
 * Study · Screen | **Dashboard** | Budget · More — the home surface sits in the middle as a raised
 * accent button rather than a flat left-most tab (Issue #129). Selection/navigation semantics are
 * unchanged: every slot still calls [onSelectPrimary] (which does the popUpTo("dashboard")
 * saveState dance) or [onMore].
 */
@Composable
private fun AppBottomBar(
    currentRoute: String?,
    onSelectPrimary: (String) -> Unit,
    onMore: () -> Unit
) {
    val left = remember {
        listOf(
            BottomDest("study_tracker", Icons.Default.Timer, R.string.module_study),
            BottomDest("screen_time", Icons.Default.Monitor, R.string.module_screen)
        )
    }
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        left.forEach { dest -> FlatTab(dest, currentRoute, onSelectPrimary) }

        // The center slot is a plain weighted Box, not a NavigationBarItem: it hosts the raised
        // button and must not get the item's own ripple/indicator on top of it.
        val onDashboard = currentRoute == "dashboard"
        // No fillMaxHeight here: inside NavigationBar's row that resolves against an unbounded
        // height constraint and stretches the whole bar over the screen.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = { onSelectPrimary("dashboard") },
                shape = CircleShape,
                color = if (onDashboard) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (onDashboard) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.size(52.dp).offset(y = (-10).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Dashboard,
                        contentDescription = stringResource(R.string.module_dashboard),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        FlatTab(
            BottomDest("budget_tracker", Icons.Default.AccountBalanceWallet, R.string.module_budget),
            currentRoute,
            onSelectPrimary
        )
        NavigationBarItem(
            selected = false,
            onClick = onMore,
            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_more)) }
        )
    }
}

@Composable
private fun RowScope.FlatTab(
    dest: BottomDest,
    currentRoute: String?,
    onSelectPrimary: (String) -> Unit
) {
    NavigationBarItem(
        selected = currentRoute == dest.route,
        onClick = { onSelectPrimary(dest.route) },
        icon = { Icon(dest.icon, contentDescription = null) },
        label = { Text(stringResource(dest.label)) }
    )
}

/** The overflow sheet from the bottom bar's "More" tab — routes not given a primary slot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            MoreRow(Icons.Default.GridView, R.string.module_overview, "overview", onSelect)
            MoreRow(Icons.Default.Notifications, R.string.module_tasks, "reminders", onSelect)
            MoreRow(Icons.AutoMirrored.Filled.Notes, R.string.module_notes, "notes", onSelect)
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, @StringRes labelRes: Int, route: String, onSelect: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onSelect(route) }
    )
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "splashScale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scaleAnim.value)
        ) {
            ApexLogo(modifier = Modifier.size(120.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_title_caps),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ApexLogo(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        drawPath(
            path = Path().apply {
                moveTo(w * 0.25f, h * 0.8f)
                lineTo(w * 0.45f, h * 0.45f)
                moveTo(w * 0.38f, h * 0.6f)
                lineTo(w * 0.75f, h * 0.6f)
                moveTo(w * 0.75f, h * 0.8f)
                lineTo(w * 0.52f, h * 0.4f)
                moveTo(w * 0.45f, h * 0.3f)
                lineTo(w * 0.5f, h * 0.22f)
                lineTo(w * 0.55f, h * 0.3f)
            },
            color = color,
            style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(currencyCode: String, onCurrencySelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Recomputed only when the setting changes; the device's own currency is appended when the
    // curated list omits it, so the current value always has a row to be selected in.
    val codes = remember(currencyCode) { (currencyPickerCodes() + currencyCode).distinct() }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(R.string.menu_currency_option, currencyCode, currencySymbol(currencyCode)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.menu_currency_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            codes.forEach { code ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_currency_option, code, currencySymbol(code))) },
                    onClick = {
                        onCurrencySelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}
