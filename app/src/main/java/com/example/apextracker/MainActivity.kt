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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun MainMenu(onModuleSelected: (String) -> Unit) {
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column {
                    Text(
                        text = "Good Day,",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Select a module to begin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Prominent module first
            val prominent = modules.first { it.isProminent }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                ProminentModuleCard(prominent, onModuleSelected)
            }

            // Others in grid
            items(modules.filter { !it.isProminent }) { module ->
                GridModuleCard(module, onModuleSelected)
            }
        }
    }
}

@Composable
fun ProminentModuleCard(module: AppModule, onModuleSelected: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onModuleSelected(module.route) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background decoration
            Canvas(modifier = Modifier.fillMaxSize().offset(x = 40.dp, y = 20.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.minDimension * 0.8f
                )
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
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
fun GridModuleCard(module: AppModule, onModuleSelected: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "scale")

    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onModuleSelected(module.route) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
