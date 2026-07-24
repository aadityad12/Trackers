package com.example.apextracker

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A full-dataset backup (Issue #121): every Room entity, so an offline-only user can save a file
 * before uninstalling / switching phones and restore it later. Distinct from the CSV export
 * (budget-only, share-sheet) — this is the complete, re-importable dataset.
 *
 * [formatVersion] is the backup schema version (bump when this container's shape changes);
 * [appDbVersion] records the Room version the data came from, for diagnostics.
 */
data class BackupData(
    val formatVersion: Int = 1,
    val appDbVersion: Int = 19,
    val exportedAt: String = "",
    val budgetItems: List<BudgetItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val studySessions: List<StudySession> = emptyList(),
    val screenTimeSessions: List<ScreenTimeSession> = emptyList(),
    val excludedApps: List<ExcludedApp> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val notes: List<Note> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val goalCompletions: List<GoalCompletion> = emptyList(),
    val appUsageLimits: List<AppUsageLimit> = emptyList()
)

/**
 * Gson configured for the entities' java.time fields (ISO strings). The Room [Converters] only
 * apply inside Room, so a plain Gson would emit broken java.time objects — these adapters keep the
 * backup human-readable and stable. Enums (RecurrenceFrequency, GoalType, DayOfWeek, …) serialize
 * by name via Gson's defaults, which is exactly what we want.
 */
fun backupGson(): Gson = GsonBuilder()
    .serializeNulls()
    .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ -> JsonPrimitive(src.toString()) })
    .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { json, _, _ -> LocalDate.parse(json.asString) })
    .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { src, _, _ -> JsonPrimitive(src.toString()) })
    .registerTypeAdapter(LocalDateTime::class.java, JsonDeserializer { json, _, _ -> LocalDateTime.parse(json.asString) })
    .registerTypeAdapter(LocalTime::class.java, JsonSerializer<LocalTime> { src, _, _ -> JsonPrimitive(src.toString()) })
    .registerTypeAdapter(LocalTime::class.java, JsonDeserializer { json, _, _ -> LocalTime.parse(json.asString) })
    .create()

fun buildBackupJson(data: BackupData): String = backupGson().toJson(data)

/**
 * Parses a backup file. Throws (Gson/date exceptions) on malformed input; the caller surfaces that
 * as a failed restore rather than corrupting the DB. A null result (Gson can return null for the
 * literal "null") is normalized to an error by the caller.
 */
fun parseBackupJson(json: String): BackupData? = backupGson().fromJson(json, BackupData::class.java)
