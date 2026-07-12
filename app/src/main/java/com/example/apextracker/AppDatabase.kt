package com.example.apextracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// MIGRATION POLICY (Issue #17): fallbackToDestructiveMigration() below DROPS EVERY
// TABLE on a version mismatch. Signed-in users get their data re-pulled from
// Firestore by the cold-start initial sync, but signed-out users lose everything.
// Any future version bump MUST ship a real Migration object. Schema JSONs are
// exported to app/schemas/ (checked in) so migrations can be written and tested.
@Database(entities = [BudgetItem::class, Category::class, Subscription::class, StudySession::class, ScreenTimeSession::class, ExcludedApp::class, Reminder::class, Note::class], version = 11, exportSchema = true)
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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
