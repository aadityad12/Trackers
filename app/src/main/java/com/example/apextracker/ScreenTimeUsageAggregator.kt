package com.example.apextracker

internal enum class ForegroundEventKind { RESUMED, PAUSED, SCREEN_OFF }

internal data class ForegroundEvent(val kind: ForegroundEventKind, val packageName: String, val timestamp: Long)

/**
 * Aggregates per-app foreground duration from a chronological event stream within [windowStart, windowEnd).
 *
 * Handles three edge cases the naive resumed/paused pairing misses:
 * - Back-to-back RESUMED events for the same app (no intervening PAUSED, e.g. multi-activity
 *   navigation within one app) no longer reset the tracked start time, so the gap between them
 *   isn't silently dropped.
 * - A session that was already in the foreground before [windowStart] (so its RESUMED event is
 *   outside the queried window) is counted starting from [windowStart] instead of being dropped
 *   entirely when its PAUSED event finally arrives.
 * - A SCREEN_OFF event closes out the current foreground app's session, so usage doesn't keep
 *   accruing while the screen is locked.
 */
internal fun aggregateForegroundDurations(
    events: List<ForegroundEvent>,
    windowStart: Long,
    windowEnd: Long
): Map<String, Long> {
    val appUsageMap = mutableMapOf<String, Long>()
    val lastEventTime = mutableMapOf<String, Long>()
    var currentForegroundApp: String? = null

    fun closeOut(pkg: String, endTimestamp: Long) {
        val startTimeForApp = lastEventTime[pkg] ?: windowStart
        appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + (endTimestamp - startTimeForApp)
        lastEventTime.remove(pkg)
    }

    for (event in events) {
        when (event.kind) {
            ForegroundEventKind.RESUMED -> {
                currentForegroundApp = event.packageName
                lastEventTime.getOrPut(event.packageName) { event.timestamp }
            }
            ForegroundEventKind.PAUSED -> {
                closeOut(event.packageName, event.timestamp)
                if (currentForegroundApp == event.packageName) {
                    currentForegroundApp = null
                }
            }
            ForegroundEventKind.SCREEN_OFF -> {
                currentForegroundApp?.let { pkg -> closeOut(pkg, event.timestamp) }
                currentForegroundApp = null
            }
        }
    }

    currentForegroundApp?.let { pkg -> closeOut(pkg, windowEnd) }

    return appUsageMap
}
