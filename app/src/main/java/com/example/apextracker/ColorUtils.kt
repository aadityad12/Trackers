package com.example.apextracker

import androidx.compose.ui.graphics.Color

/**
 * Parses a "#RRGGBB"/"#AARRGGBB" hex string into a Compose [Color], falling back to [fallback]
 * on malformed input. [Category.colorHex] can arrive via cloud sync with no validation, so any
 * render-time parse of it must be guarded.
 */
fun parseColorSafe(colorHex: String?, fallback: Color = Color.Gray): Color {
    if (colorHex == null) return fallback
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: IllegalArgumentException) {
        fallback
    }
}
