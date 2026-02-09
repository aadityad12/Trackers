package com.example.apextracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    secondary = VividBlue,
    tertiary = SoftCyan,
    background = MidnightBlack,
    surface = DeepSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = TextWhite,
    onSurface = TextWhite,
    error = ErrorCrimson,
    onError = Color.White,
    errorContainer = Color(0xFF5C0000),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextDim,
    outline = TextMuted
)

// Keep a basic light theme for anyone who might switch to it
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006978),
    secondary = Color(0xFF006978),
    tertiary = Color(0xFF006978)
)

@Composable
fun ApexTrackerTheme(
    darkTheme: Boolean = true, // App is designed for a dark aesthetic
    dynamicColor: Boolean = false, // Disable to maintain our custom theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
