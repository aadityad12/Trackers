package com.example.apextracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncThrottleTest {

    private val interval = 60_000L

    @Test
    fun `force always pushes even inside the interval`() {
        assertTrue(shouldSyncNow(nowMillis = 1_000L, lastSyncMillis = 500L, force = true, minIntervalMillis = interval))
    }

    @Test
    fun `first-ever push goes through`() {
        assertTrue(shouldSyncNow(nowMillis = 1_720_000_000_000L, lastSyncMillis = 0L, force = false, minIntervalMillis = interval))
    }

    @Test
    fun `push inside the interval is suppressed`() {
        val last = 1_000_000L
        assertFalse(shouldSyncNow(nowMillis = last + interval - 1, lastSyncMillis = last, force = false, minIntervalMillis = interval))
    }

    @Test
    fun `push at exactly the interval goes through`() {
        val last = 1_000_000L
        assertTrue(shouldSyncNow(nowMillis = last + interval, lastSyncMillis = last, force = false, minIntervalMillis = interval))
    }

    @Test
    fun `push after the interval goes through`() {
        val last = 1_000_000L
        assertTrue(shouldSyncNow(nowMillis = last + interval + 5_000, lastSyncMillis = last, force = false, minIntervalMillis = interval))
    }
}
