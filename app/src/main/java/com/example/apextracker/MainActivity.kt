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
        pendingRoute = intent.getStringExtra(EXTRA_NAVIGATE_TO)
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
        pendingRoute = intent?.getStringExtra(EXTRA_NAVIGATE_TO)

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

        NavHost(
            navController = navController,
            startDestination = "menu",
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
            composable("menu") {
                MainMenu(
                    currentTheme = currentTheme,
                    isDarkMode = isDarkMode,
                    onThemeChange = onThemeChange,
                    onDarkModeChange = onDarkModeChange,
                    currencyCode = currencyCode,
                    onCurrencyChange = onCurrencyChange,
                    onModuleSelected = { moduleRoute ->
                        navController.navigate(moduleRoute)
                    }
                )
            }
            composable("overview") {
                OverviewView(
                    onBackToMenu = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }
            composable("dashboard") {
                DashboardView(
                    onBackToMenu = { navController.popBackStack() },
                    onManageGoals = { navController.navigate("goals") }
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
    }
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

data class AppModule(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val route: String,
    val isProminent: Boolean = false,
    val enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenu(
    currentTheme: ApexTheme,
    isDarkMode: Boolean,
    onThemeChange: (ApexTheme) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    currencyCode: String,
    onCurrencyChange: (String) -> Unit,
    onModuleSelected: (String) -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val modules = remember {
        listOf(
            AppModule(R.string.module_overview, R.string.module_overview_desc, Icons.Default.GridView, "overview", true),
            AppModule(R.string.module_dashboard, R.string.module_dashboard_desc, Icons.Default.Dashboard, "dashboard"),
            AppModule(R.string.module_budget, R.string.module_budget_desc, Icons.Default.AccountBalanceWallet, "budget_tracker"),
            AppModule(R.string.module_study, R.string.module_study_desc, Icons.Default.Timer, "study_tracker"),
            AppModule(R.string.module_screen, R.string.module_screen_desc, Icons.Default.Monitor, "screen_time"),
            AppModule(R.string.module_tasks, R.string.module_tasks_desc, Icons.Default.Notifications, "reminders"),
            AppModule(R.string.module_notes, R.string.module_notes_desc, Icons.AutoMirrored.Filled.Notes, "notes")
        )
    }

    val user by authViewModel.user.collectAsState()
    val isSyncing by authViewModel.isSyncing.collectAsState()
    val signInError by authViewModel.signInError.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(signInError) {
        val error = signInError
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            authViewModel.clearSignInError()
        }
    }
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isSmallScreen = configuration.screenWidthDp < 360

    val spacing = if (isSmallScreen) 8.dp else 12.dp

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ApexLogo(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.app_title_caps),
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            letterSpacing = 2.sp
                        )
                    }
                },
                actions = {
                    if (user != null) {
                        val syncIcon = if (isSyncing) Icons.Default.CloudSync else Icons.Default.CloudDone
                        val syncTint = if (isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        
                        Icon(
                            imageVector = syncIcon,
                            contentDescription = stringResource(R.string.cd_sync_status),
                            tint = syncTint,
                            modifier = Modifier.padding(end = 8.dp).size(20.dp)
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.menu_settings),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = spacing, vertical = 4.dp)
        ) {
            val prominent = modules.first { it.isProminent }
            val others = modules.filter { !it.isProminent }

            if (isLandscape) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    ProminentModuleCard(prominent, onModuleSelected, Modifier.weight(1f))
                    Column(modifier = Modifier.weight(2.5f), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            GridModuleCard(others[0], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[1], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[2], onModuleSelected, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            GridModuleCard(others[3], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[4], onModuleSelected, Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing)) {
                    ProminentModuleCard(prominent, onModuleSelected, Modifier.weight(0.25f))
                    Column(modifier = Modifier.weight(0.75f), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            GridModuleCard(others[0], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[1], onModuleSelected, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            GridModuleCard(others[2], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[3], onModuleSelected, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            GridModuleCard(others[4], onModuleSelected, Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    if (showSettingsDialog) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    stringResource(R.string.menu_settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                // User Profile / Auth Section
                Text(
                    stringResource(R.string.menu_account),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (user != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (user?.photoUrl != null) {
                                AsyncImage(
                                    model = user?.photoUrl,
                                    contentDescription = stringResource(R.string.cd_profile_picture),
                                    modifier = Modifier.size(48.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (user?.displayName ?: "U").take(1).uppercase(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user?.displayName ?: stringResource(R.string.user_fallback), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(user?.email ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { authViewModel.signOut(context) }) {
                                Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.cd_sign_out), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Button(
                            onClick = { authViewModel.signInWithGoogle(context) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sign_in_google))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    stringResource(R.string.menu_appearance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    onClick = { onDarkModeChange(!isDarkMode) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.menu_dark_mode), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = isDarkMode, 
                            onCheckedChange = onDarkModeChange,
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    stringResource(R.string.menu_color_accent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ApexTheme.entries.forEach { theme ->
                        // The same tokens Theme.kt feeds into each scheme's `primary`, so the
                        // swatch can't drift from the theme it previews (Issue #66).
                        val themeColor = when(theme) {
                            ApexTheme.EMERALD -> EmeraldMuted
                            ApexTheme.OCEAN -> OceanPrimary
                            ApexTheme.MAGMA -> MagmaPrimary
                            ApexTheme.ROYAL -> RoyalPrimary
                        }
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(themeColor.copy(alpha = 0.1f))
                                .border(
                                    width = 2.dp,
                                    color = if (currentTheme == theme) themeColor else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onThemeChange(theme) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(themeColor)
                            ) {
                                if (currentTheme == theme) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp).align(Alignment.Center),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    stringResource(R.string.menu_currency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                CurrencyDropdown(currencyCode = currencyCode, onCurrencySelected = onCurrencyChange)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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

@Composable
fun ProminentModuleCard(module: AppModule, onModuleSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")
    val isDark = !MaterialTheme.colorScheme.background.isLight()
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onModuleSelected(module.route) },
        shape = RoundedCornerShape(24.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.White,
        shadowElevation = if (isDark) 0.dp else 2.dp,
        border = if (isDark) {
            androidx.compose.foundation.BorderStroke(
                1.dp, 
                Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent))
            )
        } else {
            androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isDark) {
                Canvas(modifier = Modifier.fillMaxSize().offset(x = 50.dp, y = (-20).dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent),
                            radius = size.maxDimension * 0.8f
                        ),
                        radius = size.maxDimension * 0.8f
                    )
                }
            }
            
            Row(
                modifier = Modifier.padding(24.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(module.titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(module.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = primaryColor,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = module.icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GridModuleCard(module: AppModule, onModuleSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "scale")
    val isDark = !MaterialTheme.colorScheme.background.isLight()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onModuleSelected(module.route) },
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.White,
        shadowElevation = if (isDark) 0.dp else 1.dp,
        border = if (isDark) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        } else {
            androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp), 
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = module.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(module.titleRes),
                    // labelLarge (Bold 14sp), not titleSmall — the menu card title is
                    // not the tracked ALL-CAPS screen-header style.
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(module.descriptionRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

// Helper to determine brightness for light mode logic
private fun Color.isLight(): Boolean {
    val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    return luminance > 0.5
}
