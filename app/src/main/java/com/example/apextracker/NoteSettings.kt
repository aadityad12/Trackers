package com.example.apextracker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.noteDataStore: DataStore<Preferences> by preferencesDataStore(name = "note_settings")

class NoteSettings(private val context: Context) {
    companion object {
        val RECYCLE_BIN_RETENTION_HOURS = intPreferencesKey("recycle_bin_retention_hours")
    }

    val recycleBinRetentionHours: Flow<Int> = context.noteDataStore.data
        .map { preferences -> preferences[RECYCLE_BIN_RETENTION_HOURS] ?: 72 }

    suspend fun setRecycleBinRetentionHours(hours: Int) {
        context.noteDataStore.edit { preferences ->
            preferences[RECYCLE_BIN_RETENTION_HOURS] = hours
        }
    }
}
