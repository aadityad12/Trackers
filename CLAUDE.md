# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Last full audit: 2026-07-07 (Firebase/auth follow-up: 2026-07-08). If you make significant architectural changes, update this file in the same session.

## Environment Setup (read this first)

- **JDK 17+ is required to run Gradle**, but the system default `java` on this machine is JDK 11 (`/usr/libexec/java_home` only lists 11 and 8). Android Studio ships a bundled JBR that works — prefix Gradle commands with:
  ```bash
  JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>
  ```
  If Android Studio isn't installed at that path, find another JDK 17+ via `/usr/libexec/java_home -V`.
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

Note: there is currently only the default boilerplate `ExampleUnitTest` — no real unit test coverage exists for any ViewModel or business logic yet.

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
- **Important reality check vs. the "fire-and-forget on every change" convention below**: as of this audit, cloud push/delete for most entities (Categories, Subscriptions, Reminders, Notes, Study sessions, Excluded apps) is only triggered from `FirebaseManager.performInitialSync()`, which runs **once**, on the null→non-null sign-in transition (`MainActivity.kt`). No ViewModel other than `BudgetViewModel` (partially) and `ScreenTimeViewModel` calls into `FirebaseManager` on create/update/delete. In practice, sync today is "sync once at sign-in," not continuous — see Known Issues.

### Theming
- `ui/theme/Theme.kt` — `ApexTrackerTheme` composable wraps Material3 with a custom `ApexTheme` enum (EMERALD, OCEAN, MAGMA, ROYAL). Both dark and light variants exist. The active theme and dark mode toggle are stored in `rememberSaveable` at the `MainActivity` level, pushed to Firestore on change, and pulled back via a live Firestore listener when signed in (bidirectional sync — see Known Issues for a possible echo-loop concern).
- `ui/theme/Color.kt` — All named color tokens. (Dead/duplicate tokens `ElectricBlue`, `CyberCyan`, `CyberGreen`, `SoftGreen` were removed in the 2026-07-07 cleanup — the first two were exact hex duplicates of `OceanPrimary`/`OceanSecondary` under a "keeping for reference" comment, and none were referenced anywhere.)
- `ui/theme/Type.kt` — Typography definitions. Only a couple of styles are actually filled in; most screens override typography ad hoc per-`Text()` call rather than through named `Typography` styles. Worth fleshing out `Typography` properly if doing a broader UI pass.
- Light-mode note: `shiftColorForLightMode()` in `Theme.kt` adjusts `primary`/`secondary` for light backgrounds but **not `tertiary`**, so tertiary accents may look washed out in light mode — likely just an oversight, not intentional.

### Background Work
- `ReminderWorker.kt` — a `CoroutineWorker` that posts a notification via the `reminder_channel` notification channel. **It is currently never enqueued anywhere in the app** — no `WorkManager.enqueue(...)` call exists for it. See Known Issues; this is the actual root cause of "reminders don't notify."

### Permissions
- `PACKAGE_USAGE_STATS` + `QUERY_ALL_PACKAGES` — Required for screen time tracking.
- `POST_NOTIFICATIONS` — Declared in the manifest for reminder notifications, but the app **never requests it at runtime** (no `registerForActivityResult`/`ActivityCompat.requestPermission` call exists anywhere). On API 33+ this means notifications would silently fail to post even if `ReminderWorker` were wired up.

## Key Conventions
- All ViewModels extend `AndroidViewModel` and access Room through `AppDatabase.getDatabase(application)`.
- **Intended** convention: Firebase sync is fire-and-forget inside `viewModelScope.launch`; local Room is always updated first. **Actual current state**: only partially true — see "Authentication & Cloud Sync" above and Known Issues.
- Light/dark mode detection in Composables uses the extension `Color.isLight()` defined at the bottom of `MainActivity.kt`.
- The `BudgetViewModel` auto-creates `BudgetItem` entries for due subscriptions on init and on any subscription change (`checkAndAddSubscriptions()`), which back-fills one `BudgetItem` per elapsed month if a subscription's renewal date is far in the past.
- Currency formatting (`String.format("%.2f", ...)` / `"%,.2f"`) and duration formatting (seconds/millis → "Xh Ym") are each hand-rolled independently in multiple files rather than through a shared utility — see Known Issues if consolidating.

## Known Issues (as of 2026-07-07 audit)

This section exists so the next work session doesn't have to rediscover these from scratch. Ordered roughly by severity/impact. None of these were fixed in the 2026-07-07 cleanup pass (which focused on redundant/dead code and build-breaking issues) — they're feature/behavior bugs appropriate for a dedicated follow-up.

Each section below is tracked as a GitHub issue, numbered in recommended fix order: [#3](https://github.com/aadityad12/Trackers/issues/3) Reminders, [#4](https://github.com/aadityad12/Trackers/issues/4) Firebase sync, [#5](https://github.com/aadityad12/Trackers/issues/5) Overview display bugs, [#6](https://github.com/aadityad12/Trackers/issues/6) Notes, [#7](https://github.com/aadityad12/Trackers/issues/7) Screen Time accounting, [#8](https://github.com/aadityad12/Trackers/issues/8) Auth polish, [#9](https://github.com/aadityad12/Trackers/issues/9) code-duplication cleanup, [#10](https://github.com/aadityad12/Trackers/issues/10) dependency bumps.

### [Issue #3] Reminders — notifications don't fire (highest-impact bug)
1. `ReminderWorker` is never enqueued via `WorkManager` anywhere in the codebase — it's unreachable dead code today. Nothing schedules a reminder to fire at its due date/time.
2. `POST_NOTIFICATIONS` runtime permission (API 33+) is declared in the manifest but never requested.
3. `RecurrencePickerDialog.kt`: both dropdowns (`Frequency`, `Ends`) are hardcoded `expanded = false` with a no-op `onExpandedChange` — **the dropdowns can never be opened**, so users can't actually pick anything but the DAILY/NEVER defaults. This is very likely the literal cause of "recurrence doesn't work properly" (commit `01f21dc`).
4. Recurrence advancement only happens when the user manually taps "complete" on a reminder (`ReminderViewModel.handleRecurringCompletion`) — there's no calendar-driven catch-up, so a missed/unopened recurring reminder just sits overdue forever.
5. Editing an existing reminder's recurrence via `RecurrencePickerDialog` always resets to defaults rather than prefilling the current `Recurrence` — combined with #3, there's no way to verify/change an existing recurring schedule through the UI.

Fix order suggestion: (a) request `POST_NOTIFICATIONS` at runtime, (b) enqueue `ReminderWorker` (likely via `WorkManager` with exact-alarm semantics — default WorkManager windows are too inexact for a due-time reminder, consider `AlarmManager.setExactAndAllowWhileIdle` instead), (c) fix the dropdown `expanded` state in `RecurrencePickerDialog.kt`, (d) prefill the picker from the reminder's existing `Recurrence` when editing.

### [Issue #4] Firebase sync — architecture inconsistencies
- **Budget items have two competing, incompatible sync paths.** `BudgetViewModel.syncItemToCloud()`/`deleteItem()` write directly to Firestore keyed by the local Room autoincrement `id` (colliding across devices/reinstalls, missing `cloudId`/`modifiedAt`/`categoryCloudId`), while `FirebaseManager`'s `pushBudgetItem`/`syncBudgetItems` expect a UUID-based `cloudId` scheme. Net effect: a locally-created budget item can end up as two different, unlinked Firestore documents. Recommend routing `BudgetViewModel` entirely through `FirebaseManager`'s existing `cloudId` scheme and deleting `BudgetViewModel`'s ad-hoc Firestore calls.
- **Sync is "once at sign-in," not continuous**, for every entity except (partially, and broken-ly) Budget/ScreenTime — see "Authentication & Cloud Sync" above. New items created/edited/deleted after sign-in aren't pushed until the next sign-out/sign-in cycle.
- `checkAndAddSubscriptions()` in `BudgetViewModel` is launched fire-and-forget from both `init` and every subscription add/update, with no mutex/transaction guarding the read-advance-write of `renewalDate` — concurrent invocations can double-insert a `BudgetItem` for the same subscription period.
- `FirebaseManager.syncReminders()`: when resolving a newly-created reminder's parent (for recurring chains) during the very first sync, if the parent hasn't been assigned a `cloudId` yet in the same batch, the child's `parentCloudId` can resolve incorrectly — order-dependent bug for first-sync recurring chains.
- Most `mapNotNull`/`as? Type ?: continue` cloud-document parsing in `FirebaseManager.kt` silently drops malformed documents with no logging, making real-world sync issues hard to diagnose (only two bare `printStackTrace()` calls exist in the whole file).

### [Issue #5] Overview module — display bugs (explicitly requested by the developer in `notes.txt`)
- `OverviewView.kt`: total spent is formatted with `String.format("%.0f", data.totalSpent)` — rounds to whole dollars instead of showing cents. Should be `"%.2f"`.
- `OverviewView.kt`: study time is displayed as `"${data.studyTimeMinutes}m"` — raw minutes only, no hours split. Same pattern (and same fix needed) applies to the screen-time display right next to it. Needs an "Xh Ym" formatter.
- `OverviewViewModel` recomputes aggregates by scanning the *entire* Budget/Study/ScreenTime tables on every combine, rather than reusing any per-date-keyed derived flow from each module's own ViewModel — not incorrect, just redundant computation worth revisiting if this becomes a performance issue.

### [Issue #6] Notes module
- Backspacing a bullet marker (e.g. `"• "`) doesn't fully clear it in one keystroke: once the trailing space is deleted, the remaining lone glyph (`"•"`) no longer matches `bulletRegex` (which requires a trailing space), so a second backspace is needed and is treated as a plain character delete, leaving a dangling glyph momentarily. Reproducible, minor but visible.
- "Indent" on a plain (non-bulleted) line silently creates a level-2 bullet rather than doing nothing — likely surprising, not obviously intentional.

### [Issue #7] Screen Time — usage accounting edge cases
- `calculateAppSpecificUsage()` in `ScreenTimeViewModel.kt` keys foreground duration purely off `ACTIVITY_RESUMED`/`PAUSED`/`STOPPED` events within today's query window. Known undercounting: multi-activity apps firing back-to-back `RESUMED` events overwrite the tracked start time; a session that started before midnight (today's query window) is dropped entirely rather than counted from `startTime`. Known possible overcounting: no handling of screen-off events, so an app can keep "accruing" after the screen locks until an explicit pause arrives.
- `aggregatedUsage` (multi-device total) is now built from a one-shot `FirebaseManager.getOtherDevicesTodayUsage()` call refreshed every ~30s alongside the existing polling loop (this was fixed as part of the 2026-07-07 build-breakage repair — see "2026-07-07 Cleanup Pass" below); there's no live Firestore listener for other devices, so cross-device totals can lag up to ~30s.

### [Issue #8] Auth
- ~~`AuthViewModel.handleSignIn()`: if the returned credential isn't a `GoogleIdTokenCredential`...~~ **Fixed** (PR [#2](https://github.com/aadityad12/Trackers/pull/2)): Credential Manager actually returns the token wrapped in a `CustomCredential`, not a bare `GoogleIdTokenCredential` — the old direct-cast check never matched in practice, so sign-in silently no-op'd on real devices. Now unwraps via `GoogleIdTokenCredential.createFrom(credential.data)` and surfaces a real error for any other credential type.
- `AuthViewModel`'s `FirebaseAuth.AuthStateListener` registered in `init` is never removed (no `onCleared()` override) — minor leak.
- `AuthViewModel.signOut()` doesn't reset `isSyncing`/`signInError` — a sign-out mid-sync could leave the sync spinner active indefinitely in the UI.
- `MainActivity.kt`: local-theme-change → Firestore write → snapshot listener fires on the writer's own local cache update → could re-drive `currentTheme`/`isDarkMode` state → re-triggers the push effect. Likely converges harmlessly (same value written back) today, but there's no guard against this echo; worth a `hasPendingLocalChange` flag if it ever causes visible flicker.

### [Issue #9] Study Tracker (code-duplication cleanup)
- `startDailyResetCheck()` polls every 30s in an unconditional `while(true)` loop for the entire ViewModel lifetime (even while not studying) to detect day rollover — acceptable given the 30s tolerance, but an always-on poll; same pattern duplicated independently in `ScreenTimeViewModel.startScreenTimeUpdates()`. Could be consolidated into one shared "periodic refresh" helper.
- Duration formatting (`formatTime`/`formatTimeCompact` in `StudyTrackerView.kt` vs `formatMillis` in `ScreenTimeTrackerView.kt`) is three independent hand-rolled implementations with different output styles/input units. Consolidate into one shared utility if touching this area.

### [Issue #10] Dependency freshness
`gradle/libs.versions.toml` pins reasonably current-for-when-written versions, but lint flags several newer releases available (AGP 8.13.2→9.2.1, Kotlin 2.1.0→2.4.0, Firebase BOM 33.6.0→34.15.0, Room 2.6.1→2.8.4, etc.). Not bumped during this cleanup pass since version bumps carry their own regression risk and deserve a dedicated pass with testing — see `./gradlew lintDebug` output for the full list.

## 2026-07-07 Cleanup Pass (what was already fixed — don't re-flag these)

- Removed a duplicate `id("com.google.gms.google-services") version "4.4.4" apply false` plugin declaration in `app/build.gradle.kts` that collided with the version-catalog alias applied on the line above it (build-breaking).
- Added a gitignored placeholder `app/google-services.json` so the project builds without real Firebase secrets (see Environment Setup above); added `app/google-services.json` to `.gitignore`.
- Added missing Gradle dependencies that were imported in source but never declared: `androidx.credentials`, `androidx.credentials:credentials-play-services-auth`, `googleid`, `coil-compose` (all present in `libs.versions.toml` already, just missing `implementation(...)` lines in `app/build.gradle.kts`) — this was a build-breaking compile error.
- Fixed `ScreenTimeViewModel.kt`/`ScreenTimeTrackerView.kt`: these referenced a `DeviceUsage` data class and `FirebaseManager.getAggregatedScreenTime()`/`uploadScreenTime()` methods that no longer existed after `FirebaseManager.kt` was replaced wholesale by a later commit with a different API (`DeviceSession`, `uploadScreenTimeSession()`, `getOtherDevicesTodayUsage()`). Updated the ViewModel/View to use the current API — `aggregatedUsage` is now a `MutableStateFlow<List<DeviceSession>>` refreshed via a one-shot call alongside the existing 30s polling loop. This was a build-breaking compile error.
- Fixed `ScreenTimeViewModel.checkPermission()`: called `AppOpsManager.unsafeCheckOpNoThrow` which requires API 29, but `minSdk` is 26 — would crash on Android 8.0–9.0 devices below API 29. Now branches on `Build.VERSION.SDK_INT` and falls back to the deprecated `checkOpNoThrow` below API 29. (Caught by `lintDebug`, which now passes with 0 errors.)
- Removed dead code: orphaned `RecurrenceConverter.kt` (duplicate of `Converters.kt`, never registered on `AppDatabase`, never referenced); `FirebaseManager.authStateFlow()` (unused, duplicated by `AuthViewModel`'s own listener); `StudySessionDao.updateSession()` (unused — all writes go through `insertSession` with `REPLACE`); `BudgetViewModel.observeCloudChanges()` (registered a Firestore snapshot listener whose body did nothing but comments, and was never removed — a no-op leak); unused imports in `BudgetTrackerView.kt` (`detectTapGestures`, `pointerInput`, `LocalHapticFeedback`, `HapticFeedbackType`, `TextDecoration`, `atan2` — leftover from removed gesture-based pie-chart code); dead color constants in `ui/theme/Color.kt` (`ElectricBlue`, `CyberCyan` — exact duplicates of `OceanPrimary`/`OceanSecondary`; `CyberGreen`, `SoftGreen` — unused).
- Renamed `alphaAnim` → `scaleAnim` in `MainActivity.kt`'s `SplashScreen` — the variable was used as a `.scale()` modifier, not alpha/opacity; the misleading name was vestigial from an earlier fade-based design, per the developer's own note in `notes.txt` about splash-screen cleanup.
- Verified: `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew lintDebug` all pass clean (lint: 0 errors after the `AppOpsManager` fix; ~45 pre-existing warnings remain, mostly dependency-freshness and a few `@OptIn`/deprecation notices — none build-blocking).

## 2026-07-08 Follow-up (PR #2)

- Set up a real Firebase project connection: registered the debug SHA-1 fingerprint in the Firebase console, downloaded the real `google-services.json` (replacing the 2026-07-07 placeholder — still gitignored, never committed).
- Fixed Google Sign-In actually completing on real devices: `AuthViewModel.handleSignIn()` only matched `credential is GoogleIdTokenCredential`, but Credential Manager's `GetGoogleIdOption` returns the token wrapped in a `CustomCredential` (type `TYPE_GOOGLE_ID_TOKEN_CREDENTIAL`) that must be unwrapped via `GoogleIdTokenCredential.createFrom(credential.data)` — Google's documented pattern. The old check never matched, so sign-in silently no-op'd: Credential Manager returned a response, `auth.signInWithCredential()` was never called, no error shown, no Firebase auth state persisted. Confirmed via live device testing (adb logs + inspecting the app's private storage for auth persistence files) before and after the fix. Verified working end-to-end on a physical Samsung device post-fix.
- Filed the remaining Known Issues above as GitHub issues [#3](https://github.com/aadityad12/Trackers/issues/3)–[#10](https://github.com/aadityad12/Trackers/issues/10), numbered in recommended fix order.

## Developer's own TODO list (from notes.txt, still current)
- Budget: "Extract from receipt" (OCR/receipt-parsing) — not started.
- Study Timer: "Always on display" support — not started.
- Ideas floated for later: home screen widgets, animated ring-chart visualizations (Canvas-based, like `ApexLogo`), biometric lock for Budget/Notes, Gemini API "Daily Apex Tip" insights.
