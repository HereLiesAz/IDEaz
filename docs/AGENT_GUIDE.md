# Agent Guide

This document is the operational manual for AI agents (Jules, Gemini) working on the IDEaz codebase.

## Core Philosophy: "No-Code" and "Post-Code"
IDEaz is not a text editor. It is an **Overlay IDE**.
*   **The User's Role:** Interact with the *running* app, select UI elements, and describe intent (e.g., "Make this button blue").
*   **The Agent's Role:** Interpret the intent, modify the source code, run tests, and deploy the update.
*   **The Interface:** The user sees the result, not the code.

## Workflow Rules

### 1. Verification is Mandatory
*   **Never assume:** Always read a file before editing it.
*   **Double-check:** After editing, read the file again to ensure the change was applied correctly.
*   **Test:** Run unit tests (`./gradlew testDebugUnitTest`) for logic changes.
*   **Build:** Ensure the app compiles (`./gradlew :app:assembleDebug`).

### 2. Documentation First
*   Before implementing a feature, update the relevant documentation (e.g., `screens.md` if changing UI).
*   If you find a discrepancy between code and docs, fix the docs.
*   Update `TODO.md` when completing tasks.

### 3. Error Handling
*   **Crash Reporting:** The IDE has a built-in `CrashHandler`. Use it.
*   **User Feedback:** Use `MainViewModel.updateMessage` to communicate status to the user.
*   **Logs:** Use standard `Log.d/i/e` with tags. The `BuildService` captures these for the user console.

### 4. Code Style
*   **Kotlin:** Follow standard Kotlin coding conventions.
*   **Compose:** Use Material 3 components.
*   **Architecture:** MVVM with Delegates.
    *   `ViewModel` holds state (`StateFlow`).
    *   `Delegate` holds logic.
    *   `Service` runs background tasks.

### 5. Git Operations
*   **No Interactive Git:** The user cannot resolve merge conflicts manually.
*   **Strategy:** "Sync and Exit" or "Force Push" (for init files).
*   **Commit Messages:** Descriptive and semantic (e.g., "Fix: ...", "Feat: ...").

## Working with Dependencies
*   **Centralized:** All versions are in `gradle/libs.versions.toml`.
*   **Offline:** The environment has limited internet. Prefer bundled tools or cached dependencies if possible.

## Debugging Tips
*   **"Bottom Sheet Absent":** Check `MainScreen.kt` conditionals.
*   **"Build Failed":** Check `BuildService` logs and `BuildOrchestrator` steps.
*   **"404 API Error":** Check `SettingsViewModel` project ID and API key.

## Common Pitfalls
*   **Hardcoded Paths:** Use `filesDir` or `cacheDir`. Never use `/sdcard` directly.
*   **Main Thread Blocking:** Use `Dispatchers.IO` for file/network ops.
*   **Missing Permissions:** Always check `canDrawOverlays` or `ReadStorage` before accessing resources.
