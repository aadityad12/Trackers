package com.example.apextracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialSyncGateTest {

    @Test
    fun `cold start with restored session syncs once`() {
        // Returning user: Firebase Auth restored the session before composition,
        // so previousUser is already non-null (wasSignedOut = false).
        assertTrue(shouldRunInitialSync(signedIn = true, wasSignedOut = false, alreadyRanThisProcess = false))
        // Same state after the first run (e.g. activity recreation on rotation): no re-run.
        assertFalse(shouldRunInitialSync(signedIn = true, wasSignedOut = false, alreadyRanThisProcess = true))
    }

    @Test
    fun `cold start signed out does not sync`() {
        assertFalse(shouldRunInitialSync(signedIn = false, wasSignedOut = true, alreadyRanThisProcess = false))
    }

    @Test
    fun `interactive sign-in syncs even if a sync already ran this process`() {
        // Sign-out then sign-in in the same process must re-sync (fresh account state).
        assertTrue(shouldRunInitialSync(signedIn = true, wasSignedOut = true, alreadyRanThisProcess = true))
        // First-ever sign-in in a process that launched signed out.
        assertTrue(shouldRunInitialSync(signedIn = true, wasSignedOut = true, alreadyRanThisProcess = false))
    }

    @Test
    fun `sign-out never syncs`() {
        assertFalse(shouldRunInitialSync(signedIn = false, wasSignedOut = false, alreadyRanThisProcess = true))
        assertFalse(shouldRunInitialSync(signedIn = false, wasSignedOut = false, alreadyRanThisProcess = false))
    }

    @Test
    fun `cold start signed in does not double-run when transition also matches`() {
        // A single LaunchedEffect pass where both triggers are true must still be one
        // decision -> one sync; the gate itself just needs to return true once here.
        assertTrue(shouldRunInitialSync(signedIn = true, wasSignedOut = true, alreadyRanThisProcess = false))
    }
}
