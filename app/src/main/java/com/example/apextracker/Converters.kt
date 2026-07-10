package com.example.apextracker

import android.util.Log
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// Pure parsing helpers (null on malformed input, never throw) so corruption handling is
// unit-testable without Android. The converters below add the sentinel/log behavior.

fun parseLocalDateSafe(value: String): LocalDate? =
    try { LocalDate.parse(value) } catch (e: Exception) { null }

fun parseLocalTimeSafe(value: String): LocalTime? =
    try { LocalTime.parse(value) } catch (e: Exception) { null }

fun parseLocalDateTimeSafe(value: String): LocalDateTime? =
    try { LocalDateTime.parse(value) } catch (e: Exception) { null }

/**
 * Parses persisted Recurrence JSON, returning null for malformed JSON or JSON that decodes to a
 * semantically invalid object (Gson bypasses Kotlin null-safety, so `frequency`/`endType` can
 * come back null from truncated data even though the type declares them non-null).
 */
fun parseRecurrenceSafe(gson: Gson, json: String): Recurrence? {
    val parsed = try {
        gson.fromJson<Recurrence>(json, object : TypeToken<Recurrence>() {}.type)
    } catch (e: Exception) {
        null
    } ?: return null
    @Suppress("SENSELESS_COMPARISON")
    return if (parsed.frequency == null || parsed.endType == null) null else parsed
}

/**
 * Room type converters. These run inside cursor mapping for every read, so they must never
 * throw: one corrupt persisted value (interrupted write, bad cloud data) would otherwise crash
 * every screen observing that table with no way for the user to recover. Corrupt values fall
 * back to a sentinel (epoch date / midnight) for non-null fields, or null for Recurrence —
 * the row stays visible (with obviously-wrong data) and deletable instead of bricking the screen.
 */
class Converters {
    private companion object {
        const val TAG = "Converters"
        // LocalDate.EPOCH is an API 34+ field on Android; minSdk is 26
        val EPOCH_DATE: LocalDate = LocalDate.of(1970, 1, 1)
    }

    private val gson = Gson()

    @TypeConverter
    fun fromRecurrence(recurrence: Recurrence?): String? {
        return gson.toJson(recurrence)
    }

    @TypeConverter
    fun toRecurrence(json: String?): Recurrence? {
        if (json == null) return null
        val parsed = parseRecurrenceSafe(gson, json)
        if (parsed == null) Log.w(TAG, "Dropping corrupt Recurrence JSON: $json")
        return parsed
    }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        if (value == null) return null
        return parseLocalDateSafe(value) ?: EPOCH_DATE.also {
            Log.w(TAG, "Corrupt LocalDate \"$value\" — substituting $it")
        }
    }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        if (value == null) return null
        return parseLocalTimeSafe(value) ?: LocalTime.MIDNIGHT.also {
            Log.w(TAG, "Corrupt LocalTime \"$value\" — substituting $it")
        }
    }

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        if (value == null) return null
        return parseLocalDateTimeSafe(value) ?: LocalDateTime.of(EPOCH_DATE, LocalTime.MIDNIGHT).also {
            Log.w(TAG, "Corrupt LocalDateTime \"$value\" — substituting $it")
        }
    }
}
