package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // Issue #85: the caller maps both ACTIVITY_PAUSED and ACTIVITY_STOPPED to PAUSED, and Android
    // emits both for a single activity transition. The duplicate used to find no open session and
    // fall back to windowStart, crediting the app from midnight once per transition.

    @Test
    fun `the duplicate close of one transition is not counted twice`() {
        // Real shape of an app switch: A pauses, B takes the foreground, A then stops.
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart + 500),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 800),
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.b", windowStart + 800),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 900)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        // Only the 300 it was actually foregrounded. Previously: 300 + (900 - 0) = 1200.
        assertEquals(300L, result["app.a"])
    }

    @Test
    fun `repeated duplicate closes late in the window don't inflate usage`() {
        // The runaway case: an app with many activity transitions. Each stale close used to add
        // (timestamp - windowStart), which grows the later in the window it lands.
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart + 100),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 200),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 300),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 900_000)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(100L, result["app.a"])
    }

    @Test
    fun `a stale close can't push an app past the length of the window`() {
        // The headline symptom of #85 was a total physically impossible for the window.
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 10),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowEnd - 1),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowEnd - 1)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertTrue(
            "usage ${result["app.a"]} exceeds the ${windowEnd - windowStart} window",
            (result["app.a"] ?: 0L) <= windowEnd - windowStart
        )
        assertEquals(10L, result["app.a"])
    }

    @Test
    fun `a pre-window session is still credited from windowStart exactly once`() {
        // The legitimate fallback must survive the fix: the first close is credited from
        // windowStart, but a duplicate of that same close is not credited again.
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 400),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 450)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(400L, result["app.a"])
    }

    @Test
    fun `an app resumed again after a pre-window session accrues both stretches`() {
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 400),
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart + 700),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 900)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(600L, result["app.a"])
    }

    @Test
    fun `two interleaved apps are each credited only their own foreground time`() {
        // Sum across apps must not exceed wall-clock: the bug let overlapping sessions double-count.
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 100),
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.b", windowStart + 100),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 110),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.b", windowStart + 300),
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart + 300),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.b", windowStart + 310),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 500)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(300L, result["app.a"])
        assertEquals(200L, result["app.b"])
        assertEquals(500L, result.values.sum())
    }

    @Test
    fun `screen-off followed by the duplicate close doesn't double-count`() {
        val events = listOf(
            ForegroundEvent(ForegroundEventKind.RESUMED, "app.a", windowStart),
            ForegroundEvent(ForegroundEventKind.SCREEN_OFF, "", windowStart + 500),
            ForegroundEvent(ForegroundEventKind.PAUSED, "app.a", windowStart + 505)
        )

        val result = aggregateForegroundDurations(events, windowStart, windowEnd)

        assertEquals(500L, result["app.a"])
    }
}
