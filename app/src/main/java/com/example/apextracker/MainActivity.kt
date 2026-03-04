package com.example.apextracker

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.apextracker.ui.theme.ApexTheme
import com.example.apextracker.ui.theme.ApexTrackerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var currentTheme by rememberSaveable { mutableStateOf(ApexTheme.EMERALD) }
            
            ApexTrackerTheme(theme = currentTheme) {
                AppNavigation(
                    currentTheme = currentTheme,
                    onThemeChange = { currentTheme = it }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(currentTheme: ApexTheme, onThemeChange: (ApexTheme) -> Unit) {
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
                    onThemeChange = onThemeChange,
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
                // Bottom left leg
                moveTo(w * 0.25f, h * 0.8f)
                lineTo(w * 0.45f, h * 0.45f)
                
                // Cross bar
                moveTo(w * 0.38f, h * 0.6f)
                lineTo(w * 0.75f, h * 0.6f)
                
                // Bottom right leg
                moveTo(w * 0.75f, h * 0.8f)
                lineTo(w * 0.52f, h * 0.4f)
                
                // Top Chevron (the "Apex")
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
fun MainMenu(currentTheme: ApexTheme, onThemeChange: (ApexTheme) -> Unit, onModuleSelected: (String) -> Unit) {
    val modules = remember {
        listOf(
            AppModule(
                title = "Overview",
                description = "Day at a glance",
                icon = Icons.Default.GridView,
                route = "overview",
                isProminent = true
            ),
            AppModule(
                title = "Budget",
                description = "Expenses",
                icon = Icons.Default.AccountBalanceWallet,
                route = "budget_tracker"
            ),
            AppModule(
                title = "Study",
                description = "Stopwatch",
                icon = Icons.Default.Timer,
                route = "study_tracker"
            ),
            AppModule(
                title = "Screen",
                description = "Usage",
                icon = Icons.Default.Monitor,
                route = "screen_time"
            ),
            AppModule(
                title = "Tasks",
                description = "Reminders",
                icon = Icons.Default.Notifications,
                route = "reminders"
            ),
            AppModule(
                title = "Notes",
                description = "Ideas",
                icon = Icons.AutoMirrored.Filled.Notes,
                route = "notes"
            )
        )
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            val prominent = modules.first { it.isProminent }
            val others = modules.filter { !it.isProminent }

            if (isLandscape) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProminentModuleCard(
                        module = prominent,
                        onModuleSelected = onModuleSelected,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Column(
                        modifier = Modifier.weight(2.5f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridModuleCard(others[0], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[1], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[2], onModuleSelected, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridModuleCard(others[3], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[4], onModuleSelected, Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProminentModuleCard(
                        module = prominent,
                        onModuleSelected = onModuleSelected,
                        modifier = Modifier.weight(0.25f)
                    )

                    Column(
                        modifier = Modifier.weight(0.75f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridModuleCard(others[0], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[1], onModuleSelected, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridModuleCard(others[2], onModuleSelected, Modifier.weight(1f))
                            GridModuleCard(others[3], onModuleSelected, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GridModuleCard(others[4], onModuleSelected, Modifier.weight(1f))
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select Theme") },
            text = {
                Column {
                    ApexTheme.values().forEach { theme ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    onThemeChange(theme)
                                    showThemeDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when(theme) {
                                            ApexTheme.EMERALD -> Color(0xFF50C878)
                                            ApexTheme.OCEAN -> Color(0xFF00B2FF)
                                            ApexTheme.MAGMA -> Color(0xFFFF5722)
                                            ApexTheme.ROYAL -> Color(0xFF9C27B0)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = theme.name.lowercase().capitalize(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (currentTheme == theme) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ProminentModuleCard(module: AppModule, onModuleSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")

    Surface(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onModuleSelected(module.route) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 2.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isSmallHeight = maxHeight < 100.dp
            
            Canvas(modifier = Modifier.fillMaxSize().offset(x = 30.dp, y = 15.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.minDimension * 0.7f
                )
            }
            
            Row(
                modifier = Modifier.padding(if (isSmallHeight) 12.dp else 20.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.title,
                        style = if (isSmallHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1
                    )
                    Text(
                        text = module.description,
                        style = if (isSmallHeight) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
                
                Surface(
                    modifier = Modifier.size(if (isSmallHeight) 40.dp else 56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = module.icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (isSmallHeight) 20.dp else 28.dp),
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

    Surface(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onModuleSelected(module.route) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val isSmall = maxHeight < 90.dp || maxWidth < 90.dp
            
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier.size(if (isSmall) 28.dp else 36.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = module.icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (isSmall) 14.dp else 18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Column {
                    Text(
                        text = module.title,
                        style = if (isSmall) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        fontSize = if (isSmall) 8.sp else 10.sp
                    )
                }
            }
        }
    }
}
