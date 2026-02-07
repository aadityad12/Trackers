package com.example.apextracker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_settings")

class ReminderSettings(private val context: Context) {
    companion object {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val ALL_DAY_NOTIFICATION_TIME = stringPreferencesKey("all_day_notification_time")
        val SPECIFIC_TIME_OFFSET_MINUTES = intPreferencesKey("specific_time_offset_minutes")
    }

    val notificationsEnabled: Flow<Boolean> = context.reminderDataStore.data
        .map { preferences -> preferences[NOTIFICATIONS_ENABLED] ?: true }

    val allDayNotificationTime: Flow<LocalTime> = context.reminderDataStore.data
        .map { preferences -> 
            val timeString = preferences[ALL_DAY_NOTIFICATION_TIME] ?: "12:00"
            LocalTime.parse(timeString)
        }

    val specificTimeOffsetMinutes: Flow<Int> = context.reminderDataStore.data
        .map { preferences -> preferences[SPECIFIC_TIME_OFFSET_MINUTES] ?: 30 }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.reminderDataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setAllDayNotificationTime(time: LocalTime) {
        context.reminderDataStore.edit { preferences ->
            preferences[ALL_DAY_NOTIFICATION_TIME] = time.toString()
        }
    }

    suspend fun setSpecificTimeOffsetMinutes(minutes: Int) {
        context.reminderDataStore.edit { preferences ->
            preferences[SPECIFIC_TIME_OFFSET_MINUTES] = minutes
        }
    }
}
