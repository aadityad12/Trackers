package com.example.apextracker

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.apextracker.ui.theme.ApexTrackerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ApexTrackerTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
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
                    onModuleSelected = { moduleRoute ->
                        navController.navigate(moduleRoute)
                    }
                )
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
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800)
    )

    LaunchedEffect(key1 = true) {
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
        
        // Drawing the "A" logo shape from the image provided
        val path = Path().apply {
            // Main triangle body
            moveTo(w * 0.1f, h * 0.85f)
            lineTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.9f, h * 0.85f)
            
            // The cross bar
            moveTo(w * 0.32f, h * 0.58f)
            lineTo(w * 0.68f, h * 0.58f)
            
            // The separation cut
            moveTo(w * 0.45f, h * 0.35f)
            lineTo(w * 0.55f, h * 0.45f)
        }

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
    val enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenu(onModuleSelected: (String) -> Unit) {
    val modules = listOf(
        AppModule(
            title = "Budget Tracker",
            description = "Track your expenses and manage categories",
            icon = Icons.Default.AccountBalanceWallet,
            route = "budget_tracker"
        ),
        AppModule(
            title = "Study Tracker",
            description = "Daily study stopwatch with history",
            icon = Icons.Default.Timer,
            route = "study_tracker"
        ),
        AppModule(
            title = "Screen Time Tracker",
            description = "Monitor your daily device usage",
            icon = Icons.Default.Monitor,
            route = "screen_time"
        ),
        AppModule(
            title = "Reminders",
            description = "Manage your daily tasks and reminders",
            icon = Icons.Default.Notifications,
            route = "reminders"
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ApexLogo(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "APEX TRACKER",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 2.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Control Center",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(modules) { module ->
                ModuleCard(module, onModuleSelected)
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun ModuleCard(module: AppModule, onModuleSelected: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = tween(100),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                enabled = module.enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onModuleSelected(module.route) },
        colors = if (module.enabled) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) 
                 else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = if (module.enabled) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.outlineVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = module.icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (module.enabled) MaterialTheme.colorScheme.onPrimaryContainer 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (module.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            
            if (module.enabled) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
