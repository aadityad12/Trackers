package com.example.apextracker

/**
 * Pure per-app-limit logic (Issue #124), no Android/Room, so it's unit-tested in
 * ScreenTimeLimitsTest. The ViewModel supplies today's per-package usage and the stored limits.
 */

/** True when [usageMillis] has reached or passed a [limitMinutes] daily budget. */
fun isOverLimit(usageMillis: Long, limitMinutes: Int): Boolean =
    limitMinutes > 0 && usageMillis >= limitMinutes * 60_000L

/**
 * The limits whose app has crossed its budget today and hasn't already been alerted for [today]
 * (ISO yyyy-MM-dd) — i.e. the notifications the polling loop should post right now. Apps with no
 * recorded usage, a non-positive limit, or an already-sent alert for today are excluded.
 */
fun limitsToNotify(
    usageByPackage: Map<String, Long>,
    limits: List<AppUsageLimit>,
    today: String
): List<AppUsageLimit> = limits.filter { limit ->
    limit.lastNotifiedDate != today &&
        isOverLimit(usageByPackage[limit.packageName] ?: 0L, limit.dailyLimitMinutes)
}
