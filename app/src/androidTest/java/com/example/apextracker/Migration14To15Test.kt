package com.example.apextracker

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises MIGRATION_14_15 against a populated v14 database on-device: seeds existing tables,
 * runs the migration (validating the resulting schema matches the exported 15.json), and asserts
 * both that pre-existing data survived and that the two new goal tables are created and writable.
 */
@RunWith(AndroidJUnit4::class)
class Migration14To15Test {

    private val testDb = "migration-14-15-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate14To15_preservesExistingData_andCreatesGoalTables() {
        // Seed a v14 database with representative rows across a few unrelated tables.
        helper.createDatabase(testDb, 14).apply {
            execSQL("INSERT INTO study_sessions (date, subject, durationSeconds) VALUES ('2026-07-10', '', 3600)")
            execSQL("INSERT INTO screen_time_sessions (date, durationMillis) VALUES ('2026-07-10', 7200000)")
            execSQL(
                "INSERT INTO budget_items (title, amount, description, date, categoryId, cloudId, modifiedAt) " +
                    "VALUES ('Coffee', 4.5, NULL, '2026-07-10', NULL, 'cid-1', 100)"
            )
            close()
        }

        // Migrate to v15; validateDroppedTables=true asserts the schema equals the exported 15.json.
        val db = helper.runMigrationsAndValidate(testDb, 15, true, MIGRATION_14_15)

        // Pre-existing data survived untouched.
        db.query("SELECT durationSeconds FROM study_sessions WHERE date='2026-07-10' AND subject=''").use {
            assertTrue("study row should survive", it.moveToFirst())
            assertEquals(3600L, it.getLong(0))
        }
        db.query("SELECT durationMillis FROM screen_time_sessions WHERE date='2026-07-10'").use {
            assertTrue("screen-time row should survive", it.moveToFirst())
            assertEquals(7200000L, it.getLong(0))
        }
        db.query("SELECT title, amount FROM budget_items WHERE cloudId='cid-1'").use {
            assertTrue("budget row should survive", it.moveToFirst())
            assertEquals("Coffee", it.getString(0))
            assertEquals(4.5, it.getDouble(1), 0.0001)
        }

        // New tables exist and accept writes (composite PK on goal_completions included).
        db.execSQL(
            "INSERT INTO goals (name, type, metric, comparator, threshold, subject, startDate, archivedDate, sortOrder, cloudId, modifiedAt) " +
                "VALUES ('Workout', 'MANUAL', NULL, NULL, NULL, NULL, '2026-07-01', NULL, 0, 'g1', 0)"
        )
        db.execSQL("INSERT INTO goal_completions (goalCloudId, date, done, modifiedAt) VALUES ('g1', '2026-07-10', 1, 0)")

        db.query("SELECT COUNT(*) FROM goals").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT done FROM goal_completions WHERE goalCloudId='g1' AND date='2026-07-10'").use {
            assertTrue("completion row should be readable", it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.close()
    }
}
