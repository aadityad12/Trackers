package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Locale is passed explicitly everywhere below — the production default is `Locale.getDefault()`,
 * which would make these assertions depend on whatever machine runs them.
 */
class CurrencyFormatTest {

    @Test
    fun `formats USD with the familiar two-decimal dollar output`() {
        assertEquals("$1,234.50", formatCurrency(1234.5, "USD", Locale.US))
    }

    @Test
    fun `formats a non-USD currency with its own symbol`() {
        assertEquals("€1,234.50", formatCurrency(1234.5, "EUR", Locale.US))
    }

    @Test
    fun `zero-decimal currency renders no minor unit`() {
        // JPY has no minor unit. NumberFormat.setCurrency() leaves fraction digits alone, so
        // without the explicit clamp this would come out "¥100.00".
        assertEquals("¥100", formatCurrency(100.0, "JPY", Locale.US))
    }

    @Test
    fun `zero-decimal currency rounds rather than truncating to the minor unit`() {
        assertEquals("¥101", formatCurrency(100.6, "JPY", Locale.US))
    }

    @Test
    fun `negative amounts keep the locale's negative currency form`() {
        assertEquals("-$42.00", formatCurrency(-42.0, "USD", Locale.US))
    }

    @Test
    fun `negative zero-decimal amounts are handled too`() {
        assertEquals("-¥100", formatCurrency(-100.0, "JPY", Locale.US))
    }

    @Test
    fun `unknown currency code falls back to USD instead of throwing`() {
        assertEquals("$5.00", formatCurrency(5.0, "ZZZ", Locale.US))
    }

    @Test
    fun `garbage currency code falls back to USD instead of throwing`() {
        assertEquals("$5.00", formatCurrency(5.0, "not a currency", Locale.US))
        assertEquals("$5.00", formatCurrency(5.0, "", Locale.US))
    }

    @Test
    fun `lowercase currency code is accepted`() {
        assertEquals("€1.00", formatCurrency(1.0, "eur", Locale.US))
    }

    @Test
    fun `formatting follows the locale's separators while honoring the chosen currency`() {
        // A German user picking USD gets German punctuation, not US.
        val formatted = formatCurrency(1234.5, "USD", Locale.GERMANY)
        assertTrue(formatted, formatted.contains("1.234,50"))
    }

    @Test
    fun `parseCurrencySafe returns null for bad input and a currency for good input`() {
        assertNull(parseCurrencySafe("ZZZ"))
        assertNull(parseCurrencySafe(""))
        assertNull(parseCurrencySafe(null))
        assertEquals("GBP", parseCurrencySafe(" gbp ")?.currencyCode)
    }

    @Test
    fun `defaultCurrencyCode reads the locale's currency`() {
        assertEquals("JPY", defaultCurrencyCode(Locale.JAPAN))
        assertEquals("USD", defaultCurrencyCode(Locale.US))
    }

    @Test
    fun `defaultCurrencyCode falls back to USD for a locale with no country`() {
        assertEquals(DEFAULT_CURRENCY_CODE, defaultCurrencyCode(Locale.ENGLISH))
    }

    @Test
    fun `picker offers the curated list unchanged when the device currency is already in it`() {
        assertEquals(COMMON_CURRENCY_CODES, currencyPickerCodes("EUR"))
    }

    @Test
    fun `picker appends the device currency when the curated list omits it`() {
        val codes = currencyPickerCodes("PLN")
        assertEquals(COMMON_CURRENCY_CODES.size + 1, codes.size)
        assertEquals("PLN", codes.last())
    }

    @Test
    fun `compact format drops the minor unit and rounds`() {
        assertEquals("$1,235", formatCurrencyCompact(1234.5, "USD", Locale.US))
        assertEquals("$0", formatCurrencyCompact(0.0, "USD", Locale.US))
    }

    @Test
    fun `compact format honors the chosen currency's symbol`() {
        assertEquals("€1,235", formatCurrencyCompact(1234.5, "EUR", Locale.US))
        assertEquals("¥100", formatCurrencyCompact(100.0, "JPY", Locale.US))
    }

    @Test
    fun `compact format rounds half up, matching the roundToInt it replaced`() {
        // NumberFormat's HALF_EVEN default would make this 1234.
        assertEquals("$1,235", formatCurrencyCompact(1234.5, "USD", Locale.US))
        assertEquals("$1,236", formatCurrencyCompact(1235.5, "USD", Locale.US))
    }

    @Test
    fun `compact format falls back to USD for an unknown code`() {
        assertEquals("$10", formatCurrencyCompact(10.0, "ZZZ", Locale.US))
    }

    @Test
    fun `compact format puts the symbol where the locale wants it`() {
        // The old implementation concatenated a leading "$" unconditionally; German suffixes it.
        val formatted = formatCurrencyCompact(1234.0, "EUR", Locale.GERMANY)
        assertTrue(formatted, formatted.startsWith("1.234"))
    }

    @Test
    fun `currencySymbol falls back to the code itself for an unknown currency`() {
        assertEquals("$", currencySymbol("USD", Locale.US))
        assertEquals("ZZZ", currencySymbol("ZZZ", Locale.US))
    }
}
