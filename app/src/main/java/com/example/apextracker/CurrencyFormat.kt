package com.example.apextracker

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Used when no setting is stored and the device locale doesn't name a currency either. */
const val DEFAULT_CURRENCY_CODE = "USD"

/**
 * Codes offered by the picker. Curated rather than `Currency.getAvailableCurrencies()` (~300
 * entries, unusable in a bottom sheet without a search field). `currencyPickerCodes()` appends the
 * device's own currency when it isn't in here, so nobody is locked out by the curation.
 */
val COMMON_CURRENCY_CODES = listOf(
    "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF",
    "CNY", "INR", "BRL", "MXN", "KRW", "SGD", "ZAR"
)

/**
 * `Currency.getInstance` throws on anything that isn't a known ISO 4217 code, and the code can come
 * from persisted prefs or a Firestore doc written by another client — neither is trustworthy.
 * Same fall-back-don't-crash convention as `parseColorSafe` / `Converters.parse*Safe`.
 */
fun parseCurrencySafe(code: String?): Currency? {
    val normalized = code?.trim()?.uppercase(Locale.ROOT) ?: return null
    return try {
        Currency.getInstance(normalized)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** The device locale's currency, so a fresh install is already right for most users. */
fun defaultCurrencyCode(locale: Locale = Locale.getDefault()): String = try {
    Currency.getInstance(locale).currencyCode
} catch (e: IllegalArgumentException) {
    // Locales with no country ("en") or a country ISO 4217 doesn't cover land here.
    DEFAULT_CURRENCY_CODE
}

fun currencyPickerCodes(deviceCode: String = defaultCurrencyCode()): List<String> =
    if (deviceCode in COMMON_CURRENCY_CODES) COMMON_CURRENCY_CODES else COMMON_CURRENCY_CODES + deviceCode

/** Picker-row symbol ("$", "¥", or the code itself where the locale has no symbol). */
fun currencySymbol(code: String, locale: Locale = Locale.getDefault()): String =
    parseCurrencySafe(code)?.getSymbol(locale) ?: code

/**
 * The one place currency rendering happens (precedent: DurationFormat.kt).
 *
 * Amounts are stored as currency-less numbers; [currencyCode] is the user's setting (see
 * `CurrencySettings`), and [locale] supplies grouping/decimal conventions — the device's, so a
 * German user sees "1.234,50 $" rather than being forced into US punctuation by their currency
 * choice. Unknown codes fall back to USD rather than throwing.
 */
fun formatCurrency(
    amount: Double,
    currencyCode: String,
    locale: Locale = Locale.getDefault()
): String {
    val currency = parseCurrencySafe(currencyCode) ?: Currency.getInstance(DEFAULT_CURRENCY_CODE)
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = currency
    // setCurrency() deliberately leaves the fraction digits alone, so a zero-decimal currency would
    // otherwise inherit the locale's 2 and render "¥100.00". defaultFractionDigits is -1 for
    // pseudo-currencies (XXX, XAU) — clamp instead of letting that reach setMinimumFractionDigits.
    val digits = currency.defaultFractionDigits.coerceAtLeast(0)
    format.minimumFractionDigits = digits
    format.maximumFractionDigits = digits
    return format.format(amount)
}

/**
 * Whole-unit variant for the trend chart's cramped axis labels, where [formatCurrency]'s minor unit
 * doesn't fit. Lives here rather than in BudgetTrends.kt so currency rendering stays in one file —
 * it used to concatenate a literal "$" onto a US-grouped integer, which both ignored the setting and
 * put the symbol on the wrong side in locales that suffix it.
 */
fun formatCurrencyCompact(
    amount: Double,
    currencyCode: String,
    locale: Locale = Locale.getDefault()
): String {
    val format = NumberFormat.getCurrencyInstance(locale)
    format.currency = parseCurrencySafe(currencyCode) ?: Currency.getInstance(DEFAULT_CURRENCY_CODE)
    format.minimumFractionDigits = 0
    format.maximumFractionDigits = 0
    // NumberFormat defaults to HALF_EVEN, but the implementation this replaced rounded through
    // roundToInt() (HALF_UP). Pinned so the axis labels don't silently shift by 1 (1234.5 → 1235).
    // formatCurrency above deliberately keeps the HALF_EVEN default it has always had — changing
    // rounding for every amount in the app is out of scope for a currency-selection change.
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(amount)
}
