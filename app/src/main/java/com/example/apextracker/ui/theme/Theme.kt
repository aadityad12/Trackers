package com.example.apextracker.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldMuted,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E3A2B), // Very deep green for container
    onPrimaryContainer = MintGlass,
    secondary = SeafoamMuted,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1B332F),
    onSecondaryContainer = SeafoamMuted,
    tertiary = MintGlass,
    onTertiary = Color.Black,
    background = DeepCharcoal,
    onBackground = OffWhite,
    surface = DarkGunmetal,
    onSurface = OffWhite,
    surfaceVariant = DeepSlate,
    onSurfaceVariant = SlateDim,
    outline = BorderMuted,
    error = SoftRed,
    onError = Color.Black,
    errorContainer = Color(0xFF5C2B2B),
    onErrorContainer = SoftRed
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2D7D46),
    secondary = Color(0xFF3B7A6E),
    tertiary = Color(0xFF3B5D7A)
)

@Composable
fun ApexTrackerTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
