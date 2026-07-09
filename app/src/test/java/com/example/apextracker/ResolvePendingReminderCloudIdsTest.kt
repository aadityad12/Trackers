package com.example.apextracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ResolvePendingReminderCloudIdsTest {

    private fun idGenerator(prefix: String): () -> String {
        var counter = 0
        return { "$prefix-${counter++}" }
    }

    @Test
    fun `child links to parent's freshly-assigned cloudId when parent is processed first`() {
        val parent = Reminder(id = 1, name = "Parent", date = LocalDate.of(2026, 7, 10))
        val child = Reminder(id = 2, name = "Child", date = LocalDate.of(2026, 7, 17), parentId = 1)

        val result = resolvePendingReminderCloudIds(
            remindersNeedingCloudId = listOf(parent, child),
            existingCloudIdsById = emptyMap(),
            generateCloudId = idGenerator("cid")
        )

        val resolvedParent = result.first { it.id == 1L }
        val resolvedChild = result.first { it.id == 2L }
        assertEquals(resolvedParent.cloudId, resolvedChild.parentCloudId)
    }

    @Test
    fun `child links to parent's cloudId even when child is processed first in the batch`() {
        // This is the actual bug: the old implementation snapshotted cloudIds once up front,
        // so a child appearing before its parent in iteration order never saw the parent's
        // newly-assigned cloudId.
        val parent = Reminder(id = 1, name = "Parent", date = LocalDate.of(2026, 7, 10))
        val child = Reminder(id = 2, name = "Child", date = LocalDate.of(2026, 7, 17), parentId = 1)

        val result = resolvePendingReminderCloudIds(
            remindersNeedingCloudId = listOf(child, parent),
            existingCloudIdsById = emptyMap(),
            generateCloudId = idGenerator("cid")
        )

        val resolvedParent = result.first { it.id == 1L }
        val resolvedChild = result.first { it.id == 2L }
        assertEquals(resolvedParent.cloudId, resolvedChild.parentCloudId)
    }

    @Test
    fun `parent that already has a cloudId from a previous sync is still resolved for a new child`() {
        val child = Reminder(id = 2, name = "Child", date = LocalDate.of(2026, 7, 17), parentId = 1)

        val result = resolvePendingReminderCloudIds(
            remindersNeedingCloudId = listOf(child),
            existingCloudIdsById = mapOf(1L to "parent-already-synced-cid"),
            generateCloudId = idGenerator("cid")
        )

        assertEquals("parent-already-synced-cid", result.single().parentCloudId)
    }

    @Test
    fun `reminder with no parent gets a null parentCloudId`() {
        val standalone = Reminder(id = 1, name = "Standalone", date = LocalDate.of(2026, 7, 10))

        val result = resolvePendingReminderCloudIds(
            remindersNeedingCloudId = listOf(standalone),
            existingCloudIdsById = emptyMap(),
            generateCloudId = idGenerator("cid")
        )

        assertNull(result.single().parentCloudId)
    }
}
