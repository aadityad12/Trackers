package com.example.apextracker

/**
 * Gate for throttled fire-and-forget cloud pushes. The study timer saves to Room every
 * second while running; pushing each save would spam Firestore. Significant events
 * (pause, reset, day rollover) force a push; the running loop heartbeats at most once
 * per [minIntervalMillis].
 */
internal fun shouldSyncNow(
    nowMillis: Long,
    lastSyncMillis: Long,
    force: Boolean,
    minIntervalMillis: Long
): Boolean = force || nowMillis - lastSyncMillis >= minIntervalMillis
