# ApexTracker

A single Android app that consolidates the trackers you'd otherwise juggle across five different apps: budget, study time, screen time, reminders, and notes. Built with Jetpack Compose and Material 3, backed by Room for local persistence, with optional Firebase (Auth + Firestore) sync across devices.

<p align="center">
  <img src="docs/screenshots/hero.png" alt="ApexTracker app banner" width="800">
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-26-blue">
  <img alt="Target SDK" src="https://img.shields.io/badge/targetSdk-35-blue">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.0-7F52FF">
  <img alt="Compose BOM" src="https://img.shields.io/badge/Compose%20BOM-2026.06.01-4285F4">
  <img alt="AGP" src="https://img.shields.io/badge/AGP-9.2.1-3DDC84">
</p>

---

## Table of Contents

- [Overview](#overview)
- [Screenshots](#screenshots)
- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Firebase Setup](#firebase-setup)
- [Building & Testing](#building--testing)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Overview

ApexTracker is an MVVM Android app organized as six independent tracker modules living behind a single bottom-level menu screen. Every module works fully offline first (Room is the source of truth); signing in with Google adds optional Firestore-backed sync so data can follow you across devices.

<p align="center">
  <img src="docs/screenshots/splash-to-menu.gif" alt="Splash screen transitioning into the main menu" width="260">
</p>

## Screenshots

| Overview | Budget Tracker | Study Tracker |
|---|---|---|
| <img src="docs/screenshots/overview.png" width="260"> | <img src="docs/screenshots/budget-tracker.png" width="260"> | <img src="docs/screenshots/study-tracker.png" width="260"> |

| Screen Time | Reminders | Notes |
|---|---|---|
| <img src="docs/screenshots/screen-time.png" width="260"> | <img src="docs/screenshots/reminders.png" width="260"> | <img src="docs/screenshots/notes.png" width="260"> |

<p align="center">
  <img src="docs/screenshots/theme-switch.gif" alt="Switching between light and dark, and between color themes" width="260">
</p>

> Screenshots and GIFs above are placeholders — drop your own captures into `docs/screenshots/` using the same filenames (or update the paths) before publishing.

## Features

**Budget Tracker**
- Track one-off budget items alongside recurring subscriptions, grouped by category.
- Subscriptions auto-generate their next `BudgetItem` on their renewal date, including back-filling months that were missed while the app wasn't open.
- Optional calendar view of spending by day.

**Study Tracker**
- Stopwatch-style session timer with HH:MM:SS display.
- Historical session log with compact duration summaries.

**Screen Time**
- Per-app foreground usage tracked via `UsageStatsManager`, with an excluded-apps list to keep noise (launchers, system UI) out of the totals.
- Cross-device usage comparison when signed in, refreshed on a lightweight polling loop.

**Reminders**
- One-off and recurring reminders (daily, weekly, monthly, custom day-of-week patterns) with exact `AlarmManager` scheduling.
- Notifications survive reboots via a boot-completed receiver that re-arms all active alarms.
- Falls back to inexact alarms automatically if the user revokes the exact-alarm permission.

**Notes**
- Lightweight bulleted note editor with indent/outdent support.

**Cross-cutting**
- Four selectable color themes (Emerald, Ocean, Magma, Royal), each with dedicated light and dark variants.
- Google Sign-In (Credential Manager API) with optional Firestore sync of settings, budget data, subscriptions, notes, reminders, study sessions, and per-device screen time.
- Fully usable signed out — cloud sync is additive, never required.

## Architecture

```
MainActivity
   -> AuthViewModel (Google Sign-In / FirebaseAuth)
   -> FirebaseManager (Firestore read/write)
   -> AppNavigation (NavHost)
        -> menu
        -> overview        -> OverviewView        -> OverviewViewModel
        -> budget_tracker   -> BudgetTrackerView    -> BudgetViewModel
        -> study_tracker    -> StudyTrackerView     -> StudyViewModel
        -> screen_time      -> ScreenTimeTrackerView -> ScreenTimeViewModel
        -> reminders        -> ReminderView         -> ReminderViewModel
        -> notes            -> NoteView             -> NoteViewModel
```

Each module follows the same shape: a Compose `View`, an `AndroidViewModel`, and a Room `Entity` + `Dao` pair, all backed by a single `AppDatabase` singleton.

| Route | View | ViewModel | Entities |
|---|---|---|---|
| `overview` | `OverviewView.kt` | `OverviewViewModel.kt` | Aggregates all DAOs |
| `budget_tracker` | `BudgetTrackerView.kt` | `BudgetViewModel.kt` | `BudgetItem`, `Category`, `Subscription` |
| `study_tracker` | `StudyTrackerView.kt` | `StudyViewModel.kt` | `StudySession` |
| `screen_time` | `ScreenTimeTrackerView.kt` | `ScreenTimeViewModel.kt` | `ScreenTimeSession`, `ExcludedApp` |
| `reminders` | `ReminderView.kt` | `ReminderViewModel.kt` | `Reminder` |
| `notes` | `NoteView.kt` | `NoteViewModel.kt` | `Note` |

Reminder delivery is handled outside the Compose layer: `ReminderScheduler` sets an exact `AlarmManager` alarm per active reminder, `ReminderAlarmReceiver` enqueues a `ReminderWorker` (WorkManager) which posts the notification, and `ReminderBootReceiver` re-arms everything after a device reboot.

For the full, continuously updated architecture notes (including known limitations and in-progress work), see [CLAUDE.md](CLAUDE.md).

## Tech Stack

| Layer | Choice |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM (`AndroidViewModel` + `StateFlow`) |
| Local persistence | Room 2.8.4 |
| Cloud sync | Firebase Auth + Firestore |
| Auth | Credential Manager API + Google ID |
| Background work | WorkManager, `AlarmManager` |
| Image loading | Coil |
| Language | Kotlin 2.4.0 |
| Build | Android Gradle Plugin 9.2.1, KSP 2.3.9 |

## Getting Started

### Prerequisites

- Android Studio (recent stable channel), which bundles a JDK 17+ JBR runtime.
- An Android device or emulator running API 26 (Android 8.0) or higher.
- JDK 17+ available on your `PATH` or via Android Studio's bundled JBR — the system default `java` on some machines is older and will not run Gradle for this project.

### Clone and build

```bash
git clone https://github.com/aadityad12/Trackers.git
cd Trackers
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

(Adjust `JAVA_HOME` to wherever a JDK 17+ install lives on your machine, e.g. via `/usr/libexec/java_home -V` on macOS.)

The app builds and runs fully offline without any Firebase configuration — sign-in and cloud sync simply stay disabled until you add a `google-services.json` (see below).

## Firebase Setup

Cloud sync (Google Sign-In + Firestore) is optional but requires a real Firebase project:

1. Create a project in the [Firebase console](https://console.firebase.google.com/) and add an Android app with package name `com.example.apextracker`.
2. Register your machine's debug SHA-1 fingerprint:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
3. Download the generated `google-services.json` and place it at `app/google-services.json` (this file is gitignored and must never be committed).
4. Enable Google as a sign-in provider under Firebase Authentication, and create a Firestore database in the console.
5. Rebuild — Google Sign-In and Firestore sync will now work end-to-end.

## Building & Testing

```bash
# Debug APK
JAVA_HOME="<jdk17-path>" ./gradlew assembleDebug

# Install on a connected device
JAVA_HOME="<jdk17-path>" ./gradlew installDebug

# Unit tests
JAVA_HOME="<jdk17-path>" ./gradlew test

# Lint (NewApi / error-severity issues are build-blocking)
JAVA_HOME="<jdk17-path>" ./gradlew lintDebug

# Instrumented tests (requires a connected device/emulator)
JAVA_HOME="<jdk17-path>" ./gradlew connectedAndroidTest
```

Unit test coverage currently focuses on pure logic extracted out of the ViewModels: reminder scheduling, overview formatting, note bullet editing, pending-reminder cloud-ID resolution, and screen time usage aggregation.

## Project Structure

```
app/src/main/java/com/example/apextracker/
├── MainActivity.kt              # Entry point, theme state, navigation host
├── AuthViewModel.kt             # Google Sign-In / FirebaseAuth
├── FirebaseManager.kt           # All Firestore reads/writes
├── AppDatabase.kt               # Room singleton + DAOs
├── Converters.kt                # Room type converters
├── Budget*.kt                   # Budget tracker module
├── Study*.kt                    # Study tracker module
├── ScreenTime*.kt                # Screen time module
├── Reminder*.kt                 # Reminders module + AlarmManager/WorkManager plumbing
├── Note*.kt                     # Notes module
├── Overview*.kt                 # Cross-module aggregate dashboard
├── DurationFormat.kt            # Shared "Xh Ym" formatting
├── PeriodicRefresh.kt           # Shared 30s polling helper
└── ui/theme/                    # Theme, color tokens, typography
```

## Roadmap

- Route Budget Tracker's Firestore sync through the same `cloudId` scheme the rest of the app uses.
- Move cloud sync from "once at sign-in" to continuous, per-entity sync.
- Wire the currently-unreachable `BudgetCalendarView` into a navigable tab.
- Receipt OCR for the Budget Tracker ("extract from receipt").
- Always-on-display support for the Study Tracker.
- Home screen widgets, Canvas-based ring-chart visualizations, biometric lock for Budget/Notes, and Gemini-powered daily insights.

See [CLAUDE.md](CLAUDE.md) for the full, current list of known issues and their fix status.

## Contributing

Issues and pull requests are welcome. Please run the build, unit tests, and lint locally before opening a PR:

```bash
JAVA_HOME="<jdk17-path>" ./gradlew assembleDebug test lintDebug
```

## License

No license has been chosen for this project yet. All rights are reserved by the author until a license is added.
