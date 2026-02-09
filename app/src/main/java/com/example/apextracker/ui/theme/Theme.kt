package com.example.apextracker.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ApexWhite,
    secondary = ApexSilver,
    tertiary = ApexGray,
    background = ApexCharcoal,
    surface = ApexSteel,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = ApexWhite,
    onSurface = ApexWhite,
    error = ApexError,
    onError = Color.White,
    errorContainer = Color(0xFF5C0000),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceVariant = ApexGray,
    onSurfaceVariant = ApexSilver,
    outline = ApexMuted
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E1E1E),
    secondary = Color(0xFF454545),
    tertiary = Color(0xFF666666)
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
