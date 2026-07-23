package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #107 — colour swatches need a spoken name, so the hue classifier must be sane. */
class SwatchHueTest {

    @Test
    fun `primary hues classify`() {
        assertEquals(SwatchHue.RED, swatchHueOf("#ff0000"))
        assertEquals(SwatchHue.GREEN, swatchHueOf("#00ff00"))
        assertEquals(SwatchHue.BLUE, swatchHueOf("#0000ff"))
        assertEquals(SwatchHue.YELLOW, swatchHueOf("#ffff00"))
        assertEquals(SwatchHue.TEAL, swatchHueOf("#00ffff"))
        assertEquals(SwatchHue.PURPLE, swatchHueOf("#a47ae2"))
        assertEquals(SwatchHue.PINK, swatchHueOf("#f691b2"))
        assertEquals(SwatchHue.ORANGE, swatchHueOf("#ff7537"))
    }

    @Test
    fun `desaturated colours are grey`() {
        assertEquals(SwatchHue.GREY, swatchHueOf("#c2c2c2"))
        assertEquals(SwatchHue.GREY, swatchHueOf("#000000"))
        assertEquals(SwatchHue.GREY, swatchHueOf("#ffffff"))
    }

    @Test
    fun `alpha prefix is ignored`() {
        assertEquals(SwatchHue.RED, swatchHueOf("#ffff0000"))
    }

    @Test
    fun `malformed input is unknown, never a crash`() {
        assertEquals(SwatchHue.UNKNOWN, swatchHueOf(null))
        assertEquals(SwatchHue.UNKNOWN, swatchHueOf(""))
        assertEquals(SwatchHue.UNKNOWN, swatchHueOf("red"))
        assertEquals(SwatchHue.UNKNOWN, swatchHueOf("#zzzzzz"))
    }
}
