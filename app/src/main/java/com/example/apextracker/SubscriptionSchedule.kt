package com.example.apextracker

import java.time.LocalDate

/**
 * The first monthly renewal on or after [today], starting from [renewalDate] (Issue #79).
 *
 * Resuming a paused subscription uses this instead of letting `checkAndAddSubscriptions()`
 * back-fill: the whole point of pausing is that those months were *not* charged, so the skipped
 * periods must be stepped over, not invoiced. A renewal that is already in the future is returned
 * unchanged. Day-of-month clamping is `LocalDate.plusMonths`'s (Jan 31 -> Feb 28), matching how the
 * catch-up loop already advances.
 */
fun nextRenewalOnOrAfter(renewalDate: LocalDate, today: LocalDate): LocalDate {
    var next = renewalDate
    while (next.isBefore(today)) {
        next = next.plusMonths(1)
    }
    return next
}
