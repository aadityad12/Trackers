package com.example.apextracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ApexTheme {
    EMERALD, OCEAN, MAGMA, ROYAL
}

// Dynamic Color Shifter for Light Mode
private fun shiftColorForLightMode(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f) // Increase saturation more
    hsv[2] = (hsv[2] * 0.75f).coerceAtMost(1f) // Deeper brightness
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun getDarkColorScheme(primary: Color, secondary: Color, tertiary: Color): ColorScheme {
    return darkColorScheme(
        primary = primary,
        onPrimary = Color.Black,
        primaryContainer = primary.copy(alpha = 0.15f),
        onPrimaryContainer = tertiary,
        secondary = secondary,
        onSecondary = Color.Black,
        secondaryContainer = secondary.copy(alpha = 0.15f),
        onSecondaryContainer = secondary,
        tertiary = tertiary,
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
}

private fun getLightColorScheme(primary: Color, secondary: Color, tertiary: Color): ColorScheme {
    val adjPrimary = shiftColorForLightMode(primary)
    val adjSecondary = shiftColorForLightMode(secondary)
    
    return lightColorScheme(
        primary = adjPrimary,
        onPrimary = Color.White,
        primaryContainer = adjPrimary.copy(alpha = 0.1f),
        onPrimaryContainer = adjPrimary,
        secondary = adjSecondary,
        onSecondary = Color.White,
        secondaryContainer = adjSecondary.copy(alpha = 0.1f),
        onSecondaryContainer = adjSecondary,
        tertiary = tertiary,
        onTertiary = Color.White,
        background = LightBackground,
        onBackground = LightTextPrimary,
        surface = LightSurface,
        onSurface = LightTextPrimary,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightTextSecondary,
        outline = LightBorder,
        error = Color(0xFFB00020),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B)
    )
}

@Composable
fun ApexTrackerTheme(
    theme: ApexTheme = ApexTheme.EMERALD,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        when (theme) {
            ApexTheme.EMERALD -> getDarkColorScheme(EmeraldMuted, SeafoamMuted, MintGlass)
            ApexTheme.OCEAN -> getDarkColorScheme(OceanPrimary, OceanSecondary, OceanTertiary)
            ApexTheme.MAGMA -> getDarkColorScheme(MagmaPrimary, MagmaSecondary, MagmaTertiary)
            ApexTheme.ROYAL -> getDarkColorScheme(RoyalPrimary, RoyalSecondary, RoyalTertiary)
        }
    } else {
        when (theme) {
            ApexTheme.EMERALD -> getLightColorScheme(EmeraldMuted, SeafoamMuted, MintGlass)
            ApexTheme.OCEAN -> getLightColorScheme(OceanPrimary, OceanSecondary, OceanTertiary)
            ApexTheme.MAGMA -> getLightColorScheme(MagmaPrimary, MagmaSecondary, MagmaTertiary)
            ApexTheme.ROYAL -> getLightColorScheme(RoyalPrimary, RoyalSecondary, RoyalTertiary)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
