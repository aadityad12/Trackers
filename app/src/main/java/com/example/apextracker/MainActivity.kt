package com.example.apextracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.apextracker.ui.theme.ApexTrackerTheme

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

    NavHost(navController = navController, startDestination = "menu") {
        composable("menu") {
            MainMenu(
                onModuleSelected = { moduleRoute ->
                    navController.navigate(moduleRoute)
                }
            )
        }
        composable("budget_tracker") {
            BudgetTrackerApp(
                onBackToMenu = { navController.popBackStack() }
            )
        }
        composable("study_tracker") {
            StudyTrackerView(
                onBackToMenu = { navController.popBackStack() }
            )
        }
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
            description = "Coming Soon",
            icon = Icons.Default.School, // placeholder
            route = "screen_time",
            enabled = false
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Apex Tracker", fontWeight = FontWeight.Bold) })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "My Modules",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(modules) { module ->
                ModuleCard(module, onModuleSelected)
            }
        }
    }
}

@Composable
fun ModuleCard(module: AppModule, onModuleSelected: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = module.enabled) { onModuleSelected(module.route) },
        colors = if (module.enabled) CardDefaults.cardColors() 
                 else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = if (module.enabled) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.outlineVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = module.icon,
                        contentDescription = null,
                        tint = if (module.enabled) MaterialTheme.colorScheme.onPrimaryContainer 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (module.enabled) Color.Unspecified else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            if (module.enabled) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
