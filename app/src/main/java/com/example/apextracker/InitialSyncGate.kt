package com.example.apextracker

/**
 * Decides whether MainActivity should kick off a full cloud<->local initial sync
 * for the current auth state (Issue #17).
 *
 * Two triggers:
 *  - Cold start with a persisted signed-in session: Firebase Auth restores the user
 *    before composition, so the sign-in transition below never fires for returning
 *    users. Without this trigger, cross-device changes only arrive after a manual
 *    sign-out/sign-in, and a destructive Room migration would wipe local data with
 *    nothing pulling it back from Firestore.
 *  - An interactive sign-in (null -> non-null user transition), which can happen
 *    again after a sign-out in the same process.
 *
 * @param signedIn whether a Firebase user is currently present
 * @param wasSignedOut whether the previously observed auth state had no user
 * @param alreadyRanThisProcess whether an initial sync was already started in this process
 */
fun shouldRunInitialSync(
    signedIn: Boolean,
    wasSignedOut: Boolean,
    alreadyRanThisProcess: Boolean
): Boolean = signedIn && (!alreadyRanThisProcess || wasSignedOut)
