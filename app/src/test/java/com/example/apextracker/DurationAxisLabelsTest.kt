package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #97 — a sub-minute max used to render three identical "0m" gridline labels. */
class DurationAxisLabelsTest {

    @Test
    fun `sub-minute max uses seconds`() {
        assertEquals(listOf("45s", "23s", "0s"), durationAxisLabels(45_000))
        assertEquals(listOf("4s", "2s", "0s"), durationAxisLabels(4_000))
    }

    @Test
    fun `minute-scale max uses minutes`() {
        assertEquals(listOf("4m", "2m", "0m"), durationAxisLabels(4 * 60_000L))
        assertEquals(listOf("45m", "23m", "0m"), durationAxisLabels(45 * 60_000L))
    }

    @Test
    fun `hour-scale max keeps the compact h-m format`() {
        assertEquals(listOf("5h 0m", "2h 30m", "0m"), durationAxisLabels(5 * 3_600_000L))
    }

    @Test
    fun `zero and negative are safe`() {
        assertEquals(listOf("0s", "0s", "0s"), durationAxisLabels(0))
        assertEquals(listOf("0s", "0s", "0s"), durationAxisLabels(-5))
    }
}
