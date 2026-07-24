package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #124 — per-app daily screen-time limit crossing + once-per-day alert gating. */
class ScreenTimeLimitsTest {

    private fun min(m: Int) = m * 60_000L

    @Test
    fun `over limit at or past the budget`() {
        assertFalse(isOverLimit(min(29), 30))
        assertTrue(isOverLimit(min(30), 30))   // exactly at the limit counts as reached
        assertTrue(isOverLimit(min(45), 30))
    }

    @Test
    fun `non-positive limit is never over`() {
        assertFalse(isOverLimit(min(100), 0))
        assertFalse(isOverLimit(min(100), -5))
    }

    @Test
    fun `notify only crossed apps not yet alerted today`() {
        val usage = mapOf("com.a" to min(40), "com.b" to min(10), "com.c" to min(90))
        val limits = listOf(
            AppUsageLimit("com.a", 30, lastNotifiedDate = null),        // over, not yet alerted -> notify
            AppUsageLimit("com.b", 30, lastNotifiedDate = null),        // under -> skip
            AppUsageLimit("com.c", 30, lastNotifiedDate = "2026-07-24") // over but already alerted today -> skip
        )
        val result = limitsToNotify(usage, limits, "2026-07-24")
        assertEquals(listOf("com.a"), result.map { it.packageName })
    }

    @Test
    fun `an app with no usage is not notified`() {
        val limits = listOf(AppUsageLimit("com.x", 15, lastNotifiedDate = null))
        assertTrue(limitsToNotify(emptyMap(), limits, "2026-07-24").isEmpty())
    }

    @Test
    fun `yesterdays alert does not block todays`() {
        val usage = mapOf("com.a" to min(40))
        val limits = listOf(AppUsageLimit("com.a", 30, lastNotifiedDate = "2026-07-23"))
        assertEquals(1, limitsToNotify(usage, limits, "2026-07-24").size)
    }
}
