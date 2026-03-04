package com.example.apextracker.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ApexTheme {
    EMERALD, OCEAN, MAGMA, ROYAL
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

@Composable
fun ApexTrackerTheme(
    theme: ApexTheme = ApexTheme.EMERALD,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        ApexTheme.EMERALD -> getDarkColorScheme(EmeraldMuted, SeafoamMuted, MintGlass)
        ApexTheme.OCEAN -> getDarkColorScheme(OceanPrimary, OceanSecondary, OceanTertiary)
        ApexTheme.MAGMA -> getDarkColorScheme(MagmaPrimary, MagmaSecondary, MagmaTertiary)
        ApexTheme.ROYAL -> getDarkColorScheme(RoyalPrimary, RoyalSecondary, RoyalTertiary)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
