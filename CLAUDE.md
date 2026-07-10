# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Last full audit: 2026-07-07 (Firebase/auth follow-up: 2026-07-08; Known Issues fix pass + dependency bumps: 2026-07-09, branch `fix/known-issues-3-through-10`; Firebase sync unification (Issue #4): 2026-07-09, branch `fix/issue-4-firebase-sync`, merged as PR #16; bug-fix pass for issues #18–#31: 2026-07-10, merged as PRs #48/#50 — see "2026-07-10 Bug-Fix Pass" below). If you make significant architectural changes, update this file in the same session.

## Environment Setup (read this first)

- **JDK 17+ is required to run Gradle**, but the system default `java` on this machine is JDK 11 (`/usr/libexec/java_home` only lists 11 and 8). Android Studio ships a bundled JBR that works — prefix Gradle commands with:
  ```bash
  JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>
  ```
  If Android Studio isn't installed at that path, find another JDK 17+ via `/usr/libexec/java_home -V`.
- **Android Studio must be Quail 1 (2026.1.1) or newer to sync this project.** The 2026-07-09 dependency bump moved the project to AGP 9.2.1; Studio Otter 3 (2025.2.3, installed on this machine as of 2026-07-10) caps IDE sync at AGP 9.0.0 and fails with "The project is using an incompatible version (AGP 9.2.1)". Downgrading AGP instead is NOT viable: `core-ktx:1.19.0` and the `lifecycle:2.11.0` artifacts require AGP 9.1+ (AAR metadata check fails the build). **CLI Gradle is unaffected** — build/test/lint/installDebug all work regardless of Studio version, so use the terminal to install on a device if Studio hasn't been updated yet.
- **`app/google-services.json` is required by the Google Services Gradle plugin but is gitignored** (it contains real Firebase project secrets — it was committed once by accident and deleted in commit `bd3f18e`, then re-added to `.gitignore`). As of 2026-07-08 this machine has the **real** config in place (project `apex-tracker-3ed29`) with the debug SHA-1 fingerprint registered in the Firebase console — Google Sign-In is verified working end-to-end on a physical device. On a **fresh clone/new machine**, you'll need to redo this yourself: Firebase Console → Project Settings → your Android app → add your machine's debug SHA-1 (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`) → download `google-services.json` → place at `app/google-services.json`. Without it, the build still succeeds with a stub placeholder, but sign-in/Firestore won't function.

## Build & Run Commands

```bash
# Build debug APK
JAVA_HOME="<jdk17-path>" ./gradlew assembleDebug

# Install on connected device
JAVA_HOME="<jdk17-path>" ./gradlew installDebug

# Run unit tests
JAVA_HOME="<jdk17-path>" ./gradlew test

# Run lint (treat NewApi/error-severity issues as build-blocking)
JAVA_HOME="<jdk17-path>" ./gradlew lintDebug

# Run instrumented tests (requires connected device/emulator)
JAVA_HOME="<jdk17-path>" ./gradlew connectedAndroidTest

# Run a single unit test class
JAVA_HOME="<jdk17-path>" ./gradlew test --tests "com.example.apextracker.ExampleUnitTest"

# Clean build
JAVA_HOME="<jdk17-path>" ./gradlew clean
```

Note: unit tests (as of 2026-07-10) cover the pure logic extracted during the fix passes — `ReminderSchedulerTest`, `OverviewFormattingTest`, `NoteBulletEditingTest`, `ResolvePendingReminderCloudIdsTest`, `ScreenTimeUsageAggregatorTest`, `FirebaseDocParsingTest`, `LegacyBudgetDocMigrationTest`, `SyncThrottleTest`, `RecurrenceAdvanceTest`, `ConvertersSafeParsingTest`, `StudyTimerStateTest` — plus the boilerplate `ExampleUnitTest`. ViewModels themselves are still untested (they need Android framework/Robolectric).

## Architecture Overview

**ApexTracker** is an Android app built with Jetpack Compose, MVVM architecture, Room for local persistence, and Firebase (Auth + Firestore) for optional cloud sync.

### Navigation & Entry Point
- `MainActivity.kt` — single Activity; sets up `AuthViewModel`, theme state (`ApexTheme`, `isDarkMode`), and `FirebaseManager`. Passes theme callbacks down to `AppNavigation`.
- `AppNavigation` hosts a `NavHost` with these routes: `menu`, `overview`, `budget_tracker`, `study_tracker`, `screen_time`, `reminders`, `notes`.
- A splash screen (`showSplash` boolean in `AppNavigation`) runs for 2 seconds on launch before showing the `NavHost`. It's currently structured as an `if/else` that gates the entire `NavHost` inside the same composable — works correctly, but architecturally `AppNavigation` is doing two jobs (splash-gating + navigation). If touching this again, consider hoisting the splash gate above `AppNavigation` so the composable's name matches what it does.

### Modules (each has View + ViewModel + Data layer)
| Route | View | ViewModel | Entities |
|---|---|---|---|
| `overview` | `OverviewView.kt` | `OverviewViewModel.kt` | Aggregates all DAOs |
| `budget_tracker` | `BudgetTrackerView.kt` + `BudgetComponents.kt` + `BudgetCalendar.kt` | `BudgetViewModel.kt` | `BudgetItem`, `Category`, `Subscription` |
| `study_tracker` | `StudyTrackerView.kt` | `StudyViewModel.kt` | `StudySession` |
| `screen_time` | `ScreenTimeTrackerView.kt` | `ScreenTimeViewModel.kt` | `ScreenTimeSession`, `ExcludedApp` |
| `reminders` | `ReminderView.kt` | `ReminderViewModel.kt` | `Reminder` |
| `notes` | `NoteView.kt` | `NoteViewModel.kt` | `Note` |

Settings dialogs for each module live in `*Settings.kt` files (e.g., `BudgetSettings.kt`, `ReminderSettings.kt`).

`BudgetCalendar.kt`'s `BudgetCalendarView` composable is **not wired into any navigation route** — it's a complete, working calendar UI that's currently unreachable dead code from the user's perspective. Either wire it in (e.g. a tab/toggle inside `budget_tracker`) or remove it.

### Database
- `AppDatabase.kt` — Room singleton (`budget_database`), **version 11** (not 10 — bumped since this doc was last written and not updated at the time), uses `fallbackToDestructiveMigration`. Contains all 8 DAOs: `budgetDao`, `categoryDao`, `subscriptionDao`, `studySessionDao`, `screenTimeSessionDao`, `excludedAppDao`, `reminderDao`, `noteDao`.
- `Converters.kt` — Type converters for `LocalDate`/`LocalDateTime`/`Recurrence` and other non-primitive types. This is the **only** `@TypeConverters` class registered on `AppDatabase`.
- `Recurrence.kt` — Data model for recurring reminders (frequency, end condition, custom days). Persisted via `Converters.kt` (Gson round-trip).
- There used to be a separate `RecurrenceConverter.kt` with a duplicate, never-registered implementation of the same conversion logic — it was **deleted** during the 2026-07-07 cleanup pass since it was entirely dead code (not wired into `AppDatabase`, not referenced anywhere).

### Authentication & Cloud Sync
- `AuthViewModel.kt` — Manages Google Sign-In via Credential Manager API (`androidx.credentials` + `googleid`), wraps `FirebaseAuth`. Exposes `user: StateFlow<FirebaseUser?>`, `isSyncing: StateFlow<Boolean>`, and `signInError: StateFlow<String?>`.
- `FirebaseManager.kt` — Handles all Firestore operations. Syncs, under `users/{uid}/...`: app settings (theme/dark mode), budget items, categories, subscriptions, notes, reminders, study sessions, excluded apps, and per-device screen time (`users/{uid}/devices/{deviceId}/screen_time`).
- Cloud sync is optional — the app works fully offline using Room as the source of truth.
- **Sync architecture (rebuilt 2026-07-09, Issue #4, branch `fix/issue-4-firebase-sync`)**: every ViewModel holds its own `FirebaseManager(application)` and pushes/deletes fire-and-forget on every mutation via the shared `safeCloudCall()` helper (top-level in `FirebaseManager.kt`) — failures are logged, never crash, Room stays source of truth. `cloudId` (UUID) + `modifiedAt` are assigned in the ViewModel at creation time; updates bump `modifiedAt` (and assign a cloudId if empty, covering pre-existing rows). Study sessions throttle to one push per 60s while the timer runs (`shouldSyncNow()` in `SyncThrottle.kt`), forced on pause/reset/day-rollover. `performInitialSync()` still runs on the sign-in transition and now: parses each pulled doc via pure `parseXDoc()` functions (top of `FirebaseManager.kt`, unit-tested in `FirebaseDocParsingTest`) with per-doc try/catch+logging; isolates each entity behind `syncStep()` so one failure can't abort the others; migrates legacy blank-cloudId budget docs written by the old ad-hoc path (`classifyLegacyBudgetDoc()`, unit-tested in `LegacyBudgetDocMigrationTest`); and pushes **all** local rows after the pull (assigning UUIDs where missing) so data created/edited while signed out reaches the cloud. **Known limitations**: deletes performed while signed out leave the cloud doc in place (no tombstones) → the item resurrects on the next initial sync; and there are still no live Firestore listeners for entities (only settings/theme has one), so cross-device changes appear only after the next sign-in sync.

### Theming
- `ui/theme/Theme.kt` — `ApexTrackerTheme` composable wraps Material3 with a custom `ApexTheme` enum (EMERALD, OCEAN, MAGMA, ROYAL). Both dark and light variants exist. The active theme and dark mode toggle are stored in `rememberSaveable` at the `MainActivity` level, pushed to Firestore on change, and pulled back via a live Firestore listener when signed in (bidirectional sync — see Known Issues for a possible echo-loop concern).
- `ui/theme/Color.kt` — All named color tokens. (Dead/duplicate tokens `ElectricBlue`, `CyberCyan`, `CyberGreen`, `SoftGreen` were removed in the 2026-07-07 cleanup — the first two were exact hex duplicates of `OceanPrimary`/`OceanSecondary` under a "keeping for reference" comment, and none were referenced anywhere.)
- `ui/theme/Type.kt` — Typography definitions. Only a couple of styles are actually filled in; most screens override typography ad hoc per-`Text()` call rather than through named `Typography` styles. Worth fleshing out `Typography` properly if doing a broader UI pass.
- Light-mode note: `shiftColorForLightMode()` in `Theme.kt` adjusts `primary`/`secondary` for light backgrounds but **not `tertiary`**, so tertiary accents may look washed out in light mode — likely just an oversight, not intentional.

### Background Work
- `ReminderWorker.kt` — a `CoroutineWorker` that posts a notification via the `reminder_channel` notification channel. As of the 2026-07-09 fix pass it is reachable: `ReminderScheduler` (object) sets an exact `AlarmManager` alarm per active reminder → `ReminderAlarmReceiver` (BroadcastReceiver) enqueues `ReminderWorker` via WorkManager → notification posts. `ReminderBootReceiver` re-arms alarms after reboot. Scheduling is wired into every `ReminderViewModel` mutation path (add/update/toggle/delete/settings changes).

### Permissions
- `PACKAGE_USAGE_STATS` + `QUERY_ALL_PACKAGES` — Required for screen time tracking.
- `POST_NOTIFICATIONS` — Requested at runtime (API 33+) in `MainActivity.onCreate` via `registerForActivityResult` (added 2026-07-09).
- `SCHEDULE_EXACT_ALARM` + `RECEIVE_BOOT_COMPLETED` — For exact reminder alarms and re-arming them after reboot (added 2026-07-09). `ReminderScheduler` falls back to inexact `setAndAllowWhileIdle` if the user revokes exact-alarm permission on API 31+.

## Key Conventions
- All ViewModels extend `AndroidViewModel` and access Room through `AppDatabase.getDatabase(application)`.
- Firebase sync is fire-and-forget inside `viewModelScope.launch` via `safeCloudCall()`; local Room is always updated first. As of 2026-07-09 (Issue #4) this convention is actually implemented across all ViewModels — see "Authentication & Cloud Sync" above.
- Light/dark mode detection in Composables uses the extension `Color.isLight()` defined at the bottom of `MainActivity.kt`.
- The `BudgetViewModel` auto-creates `BudgetItem` entries for due subscriptions on init and on any subscription change (`checkAndAddSubscriptions()`), which back-fills one `BudgetItem` per elapsed month if a subscription's renewal date is far in the past.
- "Xh Ym" duration formatting goes through the shared `formatDurationCompact(millis)` in `DurationFormat.kt` (consolidated 2026-07-09; `StudyTrackerView.formatTime` remains separate — it's the HH:MM:SS stopwatch display, a different format). Periodic 30s polling loops go through `CoroutineScope.launchPeriodic()` in `PeriodicRefresh.kt`. Currency formatting is still hand-rolled per call site.

## Known Issues (as of 2026-07-07 audit)

This section exists so the next work session doesn't have to rediscover these from scratch. Ordered roughly by severity/impact. **2026-07-09 status update**: most of these were addressed on branch `fix/known-issues-3-through-10` (one commit per issue) — each section below is annotated with what was fixed and what remains. All fixes verified via `assembleDebug` + unit tests + `lintDebug` (0 errors) only; **no device/emulator was available**, so an on-device smoke test is still owed before closing the GitHub issues.

Each section below is tracked as a GitHub issue, numbered in recommended fix order: [#3](https://github.com/aadityad12/Trackers/issues/3) Reminders, [#4](https://github.com/aadityad12/Trackers/issues/4) Firebase sync, [#5](https://github.com/aadityad12/Trackers/issues/5) Overview display bugs, [#6](https://github.com/aadityad12/Trackers/issues/6) Notes, [#7](https://github.com/aadityad12/Trackers/issues/7) Screen Time accounting, [#8](https://github.com/aadityad12/Trackers/issues/8) Auth polish, [#9](https://github.com/aadityad12/Trackers/issues/9) code-duplication cleanup, [#10](https://github.com/aadityad12/Trackers/issues/10) dependency bumps.

### [Issue #3] Reminders — notifications don't fire (highest-impact bug) — **mostly fixed 2026-07-09**
1. ~~`ReminderWorker` never enqueued~~ **Fixed**: exact `AlarmManager` alarms via new `ReminderScheduler`/`ReminderAlarmReceiver`/`ReminderBootReceiver` (see "Background Work" above).
2. ~~`POST_NOTIFICATIONS` never requested~~ **Fixed**: requested at runtime in `MainActivity.onCreate`.
3. ~~Dropdowns hardcoded `expanded = false`~~ **Fixed**: both dropdowns in `RecurrencePickerDialog.kt` now use real `remember` state.
4. ~~Recurrence advancement only happens on manual "complete"~~ **Resolved 2026-07-10** (refiled as issue #30, Option A): overdue recurring reminders stay visible; completing one catches the chain up past today via `calculateNextOccurrenceAfter` — see the 2026-07-10 pass below.
5. ~~Recurrence picker resets to defaults when editing~~ **Fixed**: prefills from the reminder's existing `Recurrence` via new `initialRecurrence` param.

### [Issue #4] Firebase sync — architecture inconsistencies — **fixed 2026-07-09 (second pass, branch `fix/issue-4-firebase-sync`)**
- ~~Budget items' two competing sync paths~~ **Fixed**: `BudgetViewModel`'s ad-hoc `firestore`/`auth` path (`syncItemToCloud`, Room-id-keyed docs) deleted; everything routes through `FirebaseManager`'s cloudId scheme. Legacy Room-id-keyed docs in Firestore are auto-migrated/deleted during `syncBudgetItems` via `classifyLegacyBudgetDoc()` (dedup-guarded, unit-tested).
- ~~Sync is "once at sign-in," not continuous~~ **Fixed**: all ViewModels now push/delete on every mutation (see "Authentication & Cloud Sync" above). Remaining gap, deliberately out of scope: no live Firestore listeners for entities (cross-device pull still requires a sign-in sync) and no tombstones for offline deletes.
- ~~`checkAndAddSubscriptions()` race~~ **Fixed**: now guarded by a `Mutex`, and the catch-up loop calls DAOs directly instead of re-entrant public methods (which used to spawn nested launches re-triggering the check).
- ~~`syncReminders()` first-sync `parentCloudId` ordering bug~~ **Fixed**: extracted into pure `resolvePendingReminderCloudIds()` (top of `FirebaseManager.kt`) which threads batch-assigned cloudIds; unit-tested order-independent.
- ~~Cloud-document parsing silently drops malformed documents~~ **Fixed**: pure `parseXDoc()` functions throw on malformed docs; sync loops catch per-doc and `Log.w` with the doc id; `performInitialSync` isolates each entity behind `syncStep()` so one bad doc/entity can't abort the rest (previously a single bad date string aborted the whole sync).

### [Issue #5] Overview module — display bugs — **fixed 2026-07-09**
- ~~Total spent rounds to whole dollars~~ **Fixed**: `"%.2f"`.
- ~~Study/screen time shown as raw minutes~~ **Fixed**: both use `formatDurationCompact()`.
- **Still open (perf-only, not a bug)**: `OverviewViewModel` recomputes aggregates by scanning entire tables on every combine — revisit only if it becomes a performance issue.

### [Issue #6] Notes module — **partially fixed 2026-07-09**
- **Unconfirmed**: the "backspacing a bullet needs two keystrokes / leaves a dangling glyph" report did NOT reproduce through the pure edit-diffing logic — a unit test (`NoteBulletEditingTest`) shows an empty bullet line clears in one keystroke via `handleNoteContentChange`. If it happens on-device, it's likely IME-batching-specific; needs a device repro before changing the regex.
- ~~"Indent" on a plain line creates a level-2 bullet~~ **Fixed**: Indent now leaves non-bulleted lines untouched.

### [Issue #7] Screen Time — usage accounting edge cases — **fixed 2026-07-09**
- ~~Undercounting/overcounting in `calculateAppSpecificUsage()`~~ **Fixed**: event processing extracted into pure `aggregateForegroundDurations()` (`ScreenTimeUsageAggregator.kt`, unit-tested). Back-to-back `RESUMED` no longer resets the start time; a session already foregrounded before the window is counted from window start; `SCREEN_NON_INTERACTIVE` (API 28+) closes out the foreground app on screen lock.
- **Still open (accepted tradeoff)**: cross-device totals lag up to ~30s (one-shot fetch on the 30s polling loop, no live Firestore listener).

### [Issue #8] Auth — **mostly fixed**
- ~~Credential unwrap bug~~ **Fixed** earlier (PR [#2](https://github.com/aadityad12/Trackers/pull/2)).
- ~~`AuthStateListener` leak~~ **Fixed 2026-07-09**: listener stored and removed in `onCleared()`.
- ~~`signOut()` leaves `isSyncing`/`signInError` stale~~ **Fixed 2026-07-09**: both reset on sign-out.
- ~~Theme-sync echo loop~~ **Fixed 2026-07-10** (refiled as issue #31): `getSettingsFlow()` now skips snapshots with `hasPendingWrites()` — only server-acknowledged remote state drives the theme listener.

### [Issue #9] Study Tracker (code-duplication cleanup) — **fixed 2026-07-09**
- ~~Duplicated 30s polling loops~~ **Fixed**: both use `launchPeriodic()` (`PeriodicRefresh.kt`). Still always-on polls by design (30s tolerance accepted).
- ~~Three hand-rolled duration formatters~~ **Fixed**: `formatTimeCompact`/`formatMillis` merged into `formatDurationCompact()` (`DurationFormat.kt`); `formatTime` (stopwatch HH:MM:SS) intentionally kept separate.

### [Issue #10] Dependency freshness — **fixed 2026-07-09**
All catalog versions bumped to latest (AGP 9.2.1, Kotlin 2.4.0, KSP 2.3.9, Compose BOM 2026.06.01, Room 2.8.4, Firebase BOM 34.16.0, etc.), Gradle wrapper 9.1.0 → 9.4.1, compileSdk 35 → 37 (targetSdk stays 35 — no runtime behavior opt-ins). **AGP 9 migration notes**: the standalone `org.jetbrains.kotlin.android` plugin is gone (AGP 9 has built-in Kotlin and refuses it); `kotlinOptions{}` became `kotlin { compilerOptions {} }` in `app/build.gradle.kts`. KSP is standalone-versioned from 2.3.0 (no longer `<kotlin>-<ksp>` coupled). Coil intentionally left at 2.7.0 (Coil 3 = artifact/package migration, not a bump). Verified by build/tests/lint only — **needs an on-device smoke test** (sign-in, sync, each module) before merging.

## 2026-07-07 Cleanup Pass (what was already fixed — don't re-flag these)

- Removed a duplicate `id("com.google.gms.google-services") version "4.4.4" apply false` plugin declaration in `app/build.gradle.kts` that collided with the version-catalog alias applied on the line above it (build-breaking).
- Added a gitignored placeholder `app/google-services.json` so the project builds without real Firebase secrets (see Environment Setup above); added `app/google-services.json` to `.gitignore`.
- Added missing Gradle dependencies that were imported in source but never declared: `androidx.credentials`, `androidx.credentials:credentials-play-services-auth`, `googleid`, `coil-compose` (all present in `libs.versions.toml` already, just missing `implementation(...)` lines in `app/build.gradle.kts`) — this was a build-breaking compile error.
- Fixed `ScreenTimeViewModel.kt`/`ScreenTimeTrackerView.kt`: these referenced a `DeviceUsage` data class and `FirebaseManager.getAggregatedScreenTime()`/`uploadScreenTime()` methods that no longer existed after `FirebaseManager.kt` was replaced wholesale by a later commit with a different API (`DeviceSession`, `uploadScreenTimeSession()`, `getOtherDevicesTodayUsage()`). Updated the ViewModel/View to use the current API — `aggregatedUsage` is now a `MutableStateFlow<List<DeviceSession>>` refreshed via a one-shot call alongside the existing 30s polling loop. This was a build-breaking compile error.
- Fixed `ScreenTimeViewModel.checkPermission()`: called `AppOpsManager.unsafeCheckOpNoThrow` which requires API 29, but `minSdk` is 26 — would crash on Android 8.0–9.0 devices below API 29. Now branches on `Build.VERSION.SDK_INT` and falls back to the deprecated `checkOpNoThrow` below API 29. (Caught by `lintDebug`, which now passes with 0 errors.)
- Removed dead code: orphaned `RecurrenceConverter.kt` (duplicate of `Converters.kt`, never registered on `AppDatabase`, never referenced); `FirebaseManager.authStateFlow()` (unused, duplicated by `AuthViewModel`'s own listener); `StudySessionDao.updateSession()` (unused — all writes go through `insertSession` with `REPLACE`); `BudgetViewModel.observeCloudChanges()` (registered a Firestore snapshot listener whose body did nothing but comments, and was never removed — a no-op leak); unused imports in `BudgetTrackerView.kt` (`detectTapGestures`, `pointerInput`, `LocalHapticFeedback`, `HapticFeedbackType`, `TextDecoration`, `atan2` — leftover from removed gesture-based pie-chart code); dead color constants in `ui/theme/Color.kt` (`ElectricBlue`, `CyberCyan` — exact duplicates of `OceanPrimary`/`OceanSecondary`; `CyberGreen`, `SoftGreen` — unused).
- Renamed `alphaAnim` → `scaleAnim` in `MainActivity.kt`'s `SplashScreen` — the variable was used as a `.scale()` modifier, not alpha/opacity; the misleading name was vestigial from an earlier fade-based design, per the developer's own note in `notes.txt` about splash-screen cleanup.
- Verified: `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew lintDebug` all pass clean (lint: 0 errors after the `AppOpsManager` fix; ~45 pre-existing warnings remain, mostly dependency-freshness and a few `@OptIn`/deprecation notices — none build-blocking).

## 2026-07-10 Bug-Fix Pass (Issues #18–#31, PRs #48 + #50 — all merged, issues closed)

Fixed all open bug-labeled issues, one commit per issue, verified by build/tests/lint per commit plus on-device testing (Samsung SM-S931U1, Android 16). New architecture pieces to know about:

- **Recurrence advancement is now pure and lives in `Recurrence.kt`** (moved out of `ReminderViewModel`): `calculateNextDate` (respects new nullable `Recurrence.anchorDay` — anchors monthly/yearly chains to the original day-of-month so short-month clamping doesn't drift, #26), `withAnchorFrom`, and `calculateNextOccurrenceAfter` (catch-up past today for missed chains, #30 Option A: skipped periods don't count toward AFTER_OCCURRENCES). `anchorDay` is null on legacy persisted data and fills in lazily on the next advancement.
- **Reminder completion integrity**: `toggleCompletion` re-reads the row and holds a per-id in-flight guard (double-tap can't insert two next occurrences, #25); `OverviewView` routes toggles through `ReminderViewModel.toggleCompletion` instead of `OverviewViewModel`'s deleted raw-DAO flip (#18).
- **Notification tap** opens the Reminders screen: `ReminderWorker` sets a `contentIntent` with `MainActivity.EXTRA_NAVIGATE_TO`; `MainActivity` holds a `pendingRoute` (set in `onCreate`/`onNewIntent`) that `AppNavigation` consumes after the splash gate (#19).
- **Exact alarms**: the Reminders screen shows a grant banner when `ReminderScheduler.canScheduleExactAlarms()` is false (denied by default on API 33+), deep-links to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, rechecks on resume, and re-arms all alarms via `ReminderViewModel.rescheduleAll()` on grant (#21).
- **`Converters.kt` never throws**: pure `parse*Safe` helpers; corrupt dates fall back to an epoch/midnight sentinel (null would still crash non-null fields), corrupt `Recurrence` to null; `parseRecurrenceSafe` rejects Gson output with null `frequency`/`endType`. Note: `LocalDate.EPOCH` is API 34+ — lint caught this; use `LocalDate.of(1970,1,1)` (#22).
- **Study timer**: `rolloverIfNeeded()` runs synchronously in every session-write path and saves target `lastResetDate` (not `LocalDate.now()`), killing the midnight misattribution window (#27). `StudyTimerStateStore` (SharedPreferences) persists the running stopwatch; same-day process death resumes seamlessly, cross-day death credits the old day to its midnight boundary via pure `finalizeSecondsAtEndOfDay` (#24).
- **Screen time**: `calculateAppSpecificUsage()` is now `suspend` + `Dispatchers.IO` (blocking `queryEvents` + full-day event loop used to run on Main from three paths, #23).
- **Misc**: `deleteCategory` detaches referencing BudgetItems (null `categoryId`, Room + cloud) before deleting (#20); null/blank `ANDROID_ID` falls back to a persisted per-install UUID instead of shared `"unknown_device"` (#28); `parseColorSafe()` in `ColorUtils.kt` guards all category-color rendering (#29, PR #48); `getSettingsFlow()` skips `hasPendingWrites` snapshots (#31).

Remaining open GitHub issues after this pass are all enhancements (#32–#47): UI polish 1–5, real-time snapshot listeners (#37), CSV export, charts, widgets, biometric lock, OCR, etc.

## 2026-07-08 Follow-up (PR #2)

- Set up a real Firebase project connection: registered the debug SHA-1 fingerprint in the Firebase console, downloaded the real `google-services.json` (replacing the 2026-07-07 placeholder — still gitignored, never committed).
- Fixed Google Sign-In actually completing on real devices: `AuthViewModel.handleSignIn()` only matched `credential is GoogleIdTokenCredential`, but Credential Manager's `GetGoogleIdOption` returns the token wrapped in a `CustomCredential` (type `TYPE_GOOGLE_ID_TOKEN_CREDENTIAL`) that must be unwrapped via `GoogleIdTokenCredential.createFrom(credential.data)` — Google's documented pattern. The old check never matched, so sign-in silently no-op'd: Credential Manager returned a response, `auth.signInWithCredential()` was never called, no error shown, no Firebase auth state persisted. Confirmed via live device testing (adb logs + inspecting the app's private storage for auth persistence files) before and after the fix. Verified working end-to-end on a physical Samsung device post-fix.
- Filed the remaining Known Issues above as GitHub issues [#3](https://github.com/aadityad12/Trackers/issues/3)–[#10](https://github.com/aadityad12/Trackers/issues/10), numbered in recommended fix order.

## Developer's own TODO list (from notes.txt, still current)
- Budget: "Extract from receipt" (OCR/receipt-parsing) — not started.
- Study Timer: "Always on display" support — not started.
- Ideas floated for later: home screen widgets, animated ring-chart visualizations (Canvas-based, like `ApexLogo`), biometric lock for Budget/Notes, Gemini API "Daily Apex Tip" insights.
