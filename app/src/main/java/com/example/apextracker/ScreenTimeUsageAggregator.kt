package com.example.apextracker

internal enum class ForegroundEventKind { RESUMED, PAUSED, SCREEN_OFF }

internal data class ForegroundEvent(val kind: ForegroundEventKind, val packageName: String, val timestamp: Long)

/**
 * Aggregates per-app foreground duration from a chronological event stream within [windowStart, windowEnd).
 *
 * Handles four edge cases the naive resumed/paused pairing misses:
 * - Back-to-back RESUMED events for the same app (no intervening PAUSED, e.g. multi-activity
 *   navigation within one app) no longer reset the tracked start time, so the gap between them
 *   isn't silently dropped.
 * - A session that was already in the foreground before [windowStart] (so its RESUMED event is
 *   outside the queried window) is counted starting from [windowStart] instead of being dropped
 *   entirely when its PAUSED event finally arrives.
 * - A SCREEN_OFF event closes out the current foreground app's session, so usage doesn't keep
 *   accruing while the screen is locked.
 * - A PAUSED for an app with no open session is **ignored** rather than counted from [windowStart]
 *   (Issue #85). This matters because the caller collapses both ACTIVITY_PAUSED and
 *   ACTIVITY_STOPPED into [ForegroundEventKind.PAUSED], and Android emits *both* for a single
 *   activity transition. Crediting the second one from [windowStart] added `(timestamp -
 *   windowStart)` per transition — five minutes of use at 09:00 was scored as 9h11m, and the error
 *   grew with time of day. On a real device this reported 104h of usage in a 4.5-hour-old day.
 *
 * The last two rules are in tension: both concern a PAUSED with no tracked start. They're told
 * apart by whether the app has been *seen at all* in this window — only an app whose very first
 * event is a close can legitimately have been foregrounded before the window opened.
 */
internal fun aggregateForegroundDurations(
    events: List<ForegroundEvent>,
    windowStart: Long,
    windowEnd: Long
): Map<String, Long> {
    val appUsageMap = mutableMapOf<String, Long>()
    val openSessionStart = mutableMapOf<String, Long>()
    // Every package we've observed any event for. Distinguishes "was already running before the
    // window" (creditable from windowStart, once) from "duplicate/stale close" (not creditable).
    val seen = mutableSetOf<String>()
    var currentForegroundApp: String? = null

    fun credit(pkg: String, from: Long, to: Long) {
        appUsageMap[pkg] = (appUsageMap[pkg] ?: 0L) + (to - from)
    }

    for (event in events) {
        val pkg = event.packageName
        when (event.kind) {
            ForegroundEventKind.RESUMED -> {
                currentForegroundApp = pkg
                openSessionStart.getOrPut(pkg) { event.timestamp }
            }
            ForegroundEventKind.PAUSED -> {
                val start = openSessionStart.remove(pkg)
                when {
                    start != null -> credit(pkg, start, event.timestamp)
                    // First thing we've ever seen from this app is a close, so its RESUMED predates
                    // the window: it was already foregrounded when the window opened.
                    pkg !in seen -> credit(pkg, windowStart, event.timestamp)
                    // Otherwise the session is already closed and this is a duplicate (the
                    // ACTIVITY_STOPPED following an ACTIVITY_PAUSED) or a stale close. Ignore it.
                    else -> Unit
                }
                if (currentForegroundApp == pkg) {
                    currentForegroundApp = null
                }
            }
            ForegroundEventKind.SCREEN_OFF -> {
                currentForegroundApp?.let { fg ->
                    credit(fg, openSessionStart.remove(fg) ?: windowStart, event.timestamp)
                    seen.add(fg)
                }
                currentForegroundApp = null
            }
        }
        seen.add(pkg)
    }

    currentForegroundApp?.let { fg ->
        credit(fg, openSessionStart.remove(fg) ?: windowStart, windowEnd)
    }

    return appUsageMap
}
