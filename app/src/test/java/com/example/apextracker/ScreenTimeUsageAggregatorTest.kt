package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeUsageAggregatorTest {

    private val windowStart = 1_000_000L
    private val windowEnd = 2_000_000L

    @Test
    fun `simple resume-pause pair is counted normally`() {
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart + 100),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 600)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(500L, result["app.a"])
    }

    @Test
    fun `back-to-back resumed events for the same app don't drop the gap between them`() {
        // Multi-activity navigation within one app: RESUMED fires again with no intervening PAUSED.
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart),
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart + 300),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 800)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(800L, result["app.a"])
    }

    @Test
    fun `a session already in the foreground before the window starts is counted from windowStart`() {
        // No RESUMED in the window at all — only the PAUSED for a session that began earlier.
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 400)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(400L, result["app.a"])
    }

    @Test
    fun `screen-off closes out the current foreground app instead of letting it keep accruing`() {
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart),
            ForegroundEvent(ForegroundEventKind.SCREEN_OFF, "", windowStart + 500)
            // No PAUSED ever arrives (device stays locked through end of window).
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(500L, result["app.a"])
    }

    @Test
    fun `an app still in the foreground at window end is counted through windowEnd`() {
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(windowEnd - windowStart, result["app.a"])
    }
}
