package com.example.apextracker

import androidx.annotation.StringRes
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

/** Coarse colour families, enough to say aloud which swatch is which (Issue #107). */
enum class SwatchHue { RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, PINK, GREY, UNKNOWN }

/**
 * Classifies a "#RRGGBB" swatch into a nameable hue family so the colour picker can carry a
 * spoken label instead of nothing. Pure (no android.graphics) so it can be unit-tested; low
 * saturation collapses to [SwatchHue.GREY] and unparseable input to [SwatchHue.UNKNOWN].
 */
fun swatchHueOf(colorHex: String?): SwatchHue {
    val hex = colorHex?.removePrefix("#") ?: return SwatchHue.UNKNOWN
    val rgb = when (hex.length) {
        6 -> hex
        8 -> hex.substring(2) // AARRGGBB
        else -> return SwatchHue.UNKNOWN
    }
    val r = rgb.substring(0, 2).toIntOrNull(16) ?: return SwatchHue.UNKNOWN
    val g = rgb.substring(2, 4).toIntOrNull(16) ?: return SwatchHue.UNKNOWN
    val b = rgb.substring(4, 6).toIntOrNull(16) ?: return SwatchHue.UNKNOWN

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = (max - min).toDouble()
    if (max == 0 || delta / max < 0.18) return SwatchHue.GREY

    val hue = when (max) {
        r -> 60.0 * (((g - b) / delta) % 6)
        g -> 60.0 * (((b - r) / delta) + 2)
        else -> 60.0 * (((r - g) / delta) + 4)
    }.let { if (it < 0) it + 360 else it }

    return when {
        hue < 15 -> SwatchHue.RED
        hue < 40 -> SwatchHue.ORANGE
        hue < 68 -> SwatchHue.YELLOW
        hue < 160 -> SwatchHue.GREEN
        hue < 195 -> SwatchHue.TEAL
        hue < 255 -> SwatchHue.BLUE
        hue < 295 -> SwatchHue.PURPLE
        hue < 350 -> SwatchHue.PINK
        else -> SwatchHue.RED
    }
}

/** Spoken name for a swatch hue, for contentDescription use. */
@StringRes
fun swatchHueLabelRes(hue: SwatchHue): Int = when (hue) {
    SwatchHue.RED -> R.string.color_name_red
    SwatchHue.ORANGE -> R.string.color_name_orange
    SwatchHue.YELLOW -> R.string.color_name_yellow
    SwatchHue.GREEN -> R.string.color_name_green
    SwatchHue.TEAL -> R.string.color_name_teal
    SwatchHue.BLUE -> R.string.color_name_blue
    SwatchHue.PURPLE -> R.string.color_name_purple
    SwatchHue.PINK -> R.string.color_name_pink
    SwatchHue.GREY -> R.string.color_name_grey
    SwatchHue.UNKNOWN -> R.string.color_name_other
}
