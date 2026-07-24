package com.example.apextracker

/**
 * Every route registered in [AppNavigation]'s NavHost. Kept here (rather than inline in
 * MainActivity) so the intent-extra allowlist and the NavHost can be checked against one list.
 */
val APP_ROUTES = setOf(
    "dashboard",
    "goals",
    "overview",
    "budget_tracker",
    "study_tracker",
    "screen_time",
    "reminders",
    "notes"
)

/**
 * Validates a route requested through [MainActivity.EXTRA_NAVIGATE_TO].
 *
 * MainActivity is exported (it carries the LAUNCHER intent-filter), so any installed app can hand
 * it an arbitrary extra. `NavController.navigate(String)` throws IllegalArgumentException for an
 * unknown route, and the call site runs inside a LaunchedEffect — an unvalidated value crashes the
 * app on cold start (Issue #105). Anything not in [APP_ROUTES] is dropped.
 */
fun sanitizeRequestedRoute(route: String?): String? = route?.takeIf { it in APP_ROUTES }
