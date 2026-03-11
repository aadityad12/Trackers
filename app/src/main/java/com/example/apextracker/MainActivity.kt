package com.example.apextracker

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.apextracker.ui.theme.ApexTheme
import com.example.apextracker.ui.theme.ApexTrackerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val user by authViewModel.user.collectAsState()
            
            var currentTheme by rememberSaveable { mutableStateOf(ApexTheme.EMERALD) }
            var isDarkMode by rememberSaveable { mutableStateOf(true) }
            
            // Sync settings when they change if logged in
            val firebaseManager = remember { FirebaseManager(this) }
            LaunchedEffect(currentTheme, isDarkMode, user) {
                if (user != null) {
                    firebaseManager.syncSettings(currentTheme.name, isDarkMode)
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
                        }
                    }
                }
            }
            
            ApexTrackerTheme(theme = currentTheme, darkTheme = isDarkMode) {
                AppNavigation(
                    currentTheme = currentTheme,
                    isDarkMode = isDarkMode,
                    onThemeChange = { currentTheme = it },
                    onDarkModeChange = { isDarkMode = it }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    currentTheme: ApexTheme, 
    isDarkMode: Boolean,
    onThemeChange: (ApexTheme) -> Unit,
    onDarkModeChange: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    var showSplash by remember { mutableStateOf(true) }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
    } else {
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
                    onModuleSelected = { moduleRoute ->
                        navController.navigate(moduleRoute)
                    }
                )
            }
            composable("overview") {
                OverviewView(onBackToMenu = { navController.popBackStack() })
            }
            composable("budget_tracker") {
                BudgetTrackerApp(onBackToMenu = { navController.popBackStack() })
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
                NoteView(onBackToMenu = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "splashAlpha"
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
            modifier = Modifier.scale(alphaAnim.value)
        ) {
            ApexLogo(modifier = Modifier.size(120.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "APEX TRACKER",
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
    val title: String,
    val description: String,
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
    onModuleSelected: (String) -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val modules = remember {
        listOf(
            AppModule("Overview", "Day at a glance", Icons.Default.GridView, "overview", true),
            AppModule("Budget", "Expenses", Icons.Default.AccountBalanceWallet, "budget_tracker"),
            AppModule("Study", "Stopwatch", Icons.Default.Timer, "study_tracker"),
            AppModule("Screen", "Usage", Icons.Default.Monitor, "screen_time"),
            AppModule("Tasks", "Reminders", Icons.Default.Notifications, "reminders"),
            AppModule("Notes", "Ideas", Icons.AutoMirrored.Filled.Notes, "notes")
        )
    }

    val user by authViewModel.user.collectAsState()
    val isSyncing by authViewModel.isSyncing.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isSmallScreen = configuration.screenWidthDp < 360

    val spacing = if (isSmallScreen) 8.dp else 12.dp

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ApexLogo(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "APEX TRACKER",
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
                            contentDescription = "Sync Status",
                            tint = syncTint,
                            modifier = Modifier.padding(end = 8.dp).size(20.dp)
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
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
                    "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                // User Profile / Auth Section
                Text(
                    "Account",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
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
                                    contentDescription = "Profile Picture",
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
                                Text(user?.displayName ?: "User", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(user?.email ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { authViewModel.signOut(context) }) {
                                Icon(Icons.Default.Logout, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error)
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
                            Text("Sign in with Google")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
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
                        Text("Dark Mode", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = isDarkMode, 
                            onCheckedChange = onDarkModeChange,
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    "Color Accent",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ApexTheme.entries.forEach { theme ->
                        val themeColor = when(theme) {
                            ApexTheme.EMERALD -> Color(0xFF50C878)
                            ApexTheme.OCEAN -> Color(0xFF00B2FF)
                            ApexTheme.MAGMA -> Color(0xFFFF5722)
                            ApexTheme.ROYAL -> Color(0xFF9C27B0)
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
                        text = module.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = module.description,
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
                    text = module.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    fontSize = 11.sp
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
