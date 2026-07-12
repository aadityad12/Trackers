package com.example.apextracker

import java.text.NumberFormat
import java.util.Locale

/**
 * The one place currency rendering happens (precedent: DurationFormat.kt).
 *
 * Amounts are stored as currency-less numbers and the app has no currency
 * setting yet, so everything renders as USD ("$1,234.50") like it always has.
 * When a currency/locale setting exists, this is the single switch point.
 */
fun formatCurrency(amount: Double): String =
    NumberFormat.getCurrencyInstance(Locale.US).format(amount)
