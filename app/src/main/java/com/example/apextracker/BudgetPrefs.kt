package com.example.apextracker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.budgetPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "budget_settings")

/**
 * Budget-wide preferences that aren't rows in the database — currently just the overall monthly
 * spending ceiling (Issue #125), the whole-wallet counterpart to the per-category caps stored on
 * `Category.monthlyLimit` (Issue #75).
 *
 * DataStore rather than a Room row because it's a single scalar with no relations, matching
 * [CurrencySettings]/[SecuritySettings]. Like those it is **local-only** — not synced to Firestore
 * — so a signed-in user sets it per device.
 */
class BudgetPrefs(private val context: Context) {
    companion object {
        val OVERALL_MONTHLY_LIMIT = doublePreferencesKey("overall_monthly_limit")
    }

    /** null = no overall ceiling. Values that can't drive a progress bar normalize to null. */
    val overallMonthlyLimit: Flow<Double?> = context.budgetPrefsDataStore.data
        .map { prefs -> prefs[OVERALL_MONTHLY_LIMIT]?.takeIf { it > 0.0 && it.isFinite() } }

    suspend fun setOverallMonthlyLimit(limit: Double?) {
        context.budgetPrefsDataStore.edit { prefs ->
            if (limit == null || limit <= 0.0 || !limit.isFinite()) {
                prefs.remove(OVERALL_MONTHLY_LIMIT)
            } else {
                prefs[OVERALL_MONTHLY_LIMIT] = limit
            }
        }
    }
}
