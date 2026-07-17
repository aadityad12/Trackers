package com.example.apextracker

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.currencyDataStore: DataStore<Preferences> by preferencesDataStore(name = "currency_settings")

class CurrencySettings(private val context: Context) {
    companion object {
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
    }

    /**
     * Unlike theme/dark-mode (which live in `rememberSaveable` + Firestore and so only persist for
     * signed-in users), the currency is stored locally: the app is fully usable offline and an
     * amount rendered in the wrong symbol is a correctness problem, not a cosmetic one.
     */
    val currencyCode: Flow<String> = context.currencyDataStore.data
        .map { preferences -> preferences[CURRENCY_CODE] ?: defaultCurrencyCode() }

    suspend fun setCurrencyCode(code: String) {
        context.currencyDataStore.edit { preferences ->
            preferences[CURRENCY_CODE] = code
        }
    }
}

/**
 * Read by every `formatCurrency` call site. `compositionLocalOf` (not `static`) so a currency change
 * recomposes only the readers rather than the whole tree.
 */
val LocalCurrencyCode = compositionLocalOf { DEFAULT_CURRENCY_CODE }
