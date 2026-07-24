package com.example.apextracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// MIGRATION POLICY (Issue #17): fallbackToDestructiveMigration() below DROPS EVERY
// TABLE on a version mismatch. Signed-in users get their data re-pulled from
// Firestore by the cold-start initial sync, but signed-out users lose everything.
// Any future version bump MUST ship a real Migration object. Schema JSONs are
// exported to app/schemas/ (checked in) so migrations can be written and tested.
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}

// Nullable REAL: existing categories have no cap, and null is the "no cap" encoding
// (see Category.monthlyLimit), so no DEFAULT is wanted here.
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN monthlyLimit REAL")
    }
}

// Issue #78: study_sessions gains a per-subject dimension, which changes the primary key
// from (date) to (date, subject). SQLite can't ALTER a primary key in place, so this is the
// standard create-new / copy / drop / rename dance. Every pre-existing daily total is copied
// into the empty-string ("No subject") bucket for its date, so no study data is lost — the old
// aggregate simply becomes that day's uncategorized row. No SQL DEFAULT on `subject`: the entity
// declares only a Kotlin default (= ""), which Room does not emit as a column default, so adding
// one here would make TableInfo mismatch at runtime. The INSERT supplies '' explicitly instead.
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `study_sessions_new` (`date` TEXT NOT NULL, `subject` TEXT NOT NULL, " +
                "`durationSeconds` INTEGER NOT NULL, PRIMARY KEY(`date`, `subject`))"
        )
        db.execSQL(
            "INSERT INTO `study_sessions_new` (`date`, `subject`, `durationSeconds`) " +
                "SELECT `date`, '', `durationSeconds` FROM `study_sessions`"
        )
        db.execSQL("DROP TABLE `study_sessions`")
        db.execSQL("ALTER TABLE `study_sessions_new` RENAME TO `study_sessions`")
    }
}

// Issue #45-follow-up (Dashboard): two purely additive tables — `goals` (habit-style daily goals
// feeding the contribution heatmap) and `goal_completions` (per-day manual check-offs). No data
// copy, so this mirrors MIGRATION_11_12/12_13 rather than the PK-change dance in MIGRATION_13_14.
// The exact DDL matches Room's exported app/schemas/…/15.json (create-me-from-there on any change).
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `metric` TEXT, `comparator` TEXT, " +
                "`threshold` REAL, `subject` TEXT, `startDate` TEXT NOT NULL, `archivedDate` TEXT, " +
                "`sortOrder` INTEGER NOT NULL, `cloudId` TEXT NOT NULL, `modifiedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goal_completions` (`goalCloudId` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, `done` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`goalCloudId`, `date`))"
        )
    }
}

// Issue #126: reminders gain a priority. Purely additive; the NOT NULL DEFAULT matches the
// entity's Kotlin default so existing rows read back as NORMAL.
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN priority TEXT NOT NULL DEFAULT 'NORMAL'")
    }
}

// Issue #79: subscriptions can be paused. Additive; existing rows default to not paused.
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0")
    }
}

// Issue #124: per-app daily screen-time limits. New table, additive.
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_usage_limits` (`packageName` TEXT NOT NULL, " +
                "`dailyLimitMinutes` INTEGER NOT NULL, `lastNotifiedDate` TEXT, " +
                "PRIMARY KEY(`packageName`))"
        )
    }
}

// Issue #127: notes gain image attachments (a newline-separated filename list). Additive.
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN attachments TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [BudgetItem::class, Category::class, Subscription::class, StudySession::class, ScreenTimeSession::class, ExcludedApp::class, Reminder::class, Note::class, Goal::class, GoalCompletion::class, AppUsageLimit::class], version = 19, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun screenTimeSessionDao(): ScreenTimeSessionDao
    abstract fun excludedAppDao(): ExcludedAppDao
    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao
    abstract fun goalDao(): GoalDao
    abstract fun goalCompletionDao(): GoalCompletionDao
    abstract fun appUsageLimitDao(): AppUsageLimitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "budget_database"
                )
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
