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

@Database(entities = [BudgetItem::class, Category::class, Subscription::class, StudySession::class, ScreenTimeSession::class, ExcludedApp::class, Reminder::class, Note::class], version = 13, exportSchema = true)
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
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
