# IDEaz: Architecture

## 1. The Core Loop
The IDE is designed as an **Overlay**. It sits on top of the user's running app.

1.  **User Interacts:** The user uses their app normally.
2.  **User Selects:** The user drags a box over an area they want to change.
3.  **User Prompts:** The user types "Make this button blue".
4.  **IDE Acts:**
    *   Finds the relevant source code (via `ProjectAnalyzer` or Source Maps).
    *   Sends the prompt + code + context to the AI (Jules/Gemini).
    *   Receives a diff/patch.
    *   Applies the patch.
    *   Triggers a background build.
5.  **Update:** The app reloads (Hot Reload or Reinstall).

## 2. The Delegate Pattern
The `MainViewModel` was becoming a God Class. It has been refactored into **Delegates** (`ui/delegates/`).

*   **`MainViewModel`:** The coordinator. Holds the `StateFlow`s but delegates logic.
*   **`AIDelegate`:** Handles AI communication (Gemini/Jules).
*   **`BuildDelegate`:** Manages the `BuildService` connection and callbacks.
*   **`GitDelegate`:** Wraps `GitManager` for version control operations.
*   **`RepoDelegate`:** Handles GitHub API interactions (forking, cloning).
*   **`OverlayDelegate`:** Manages the `IdeazOverlayService` and selection logic.
*   **`SystemEventDelegate`:** Handles broadcast receivers (Package install, etc).
*   **`UpdateDelegate`:** Checks for IDEaz self-updates.
*   **`StateDelegate`:** Holds the mutable state variables.

## 3. The Services
*   **`IdeazOverlayService`:** The UI Overlay. This is the "Main Window" of the IDE. It operates as a Foreground Service handling the system alert window and hosting the Console (Bottom Sheet).
*   **`BuildService`:** A background (foreground) service that runs Gradle tasks and APK installation. It runs in a separate process to prevent UI freezes.
*   **`CrashReportingService`:** Catches and reports fatal crashes.

## 4. Data Flow
*   **State:** UI components observe `MainViewModel.state`.
*   **Events:** UI calls `MainViewModel.action()`, which delegates to `Delegate.action()`.
*   **Updates:** Delegates update `StateDelegate` variables, which emit new `StateFlow` values.

## 5. File System
*   **Projects:** Stored in `context.filesDir/projects/`.
*   **Imports:** Copied from `content://` URI to internal storage.
*   **Temp:** `context.cacheDir` used for download buffers.

## 6. Networking
*   **Retrofit:** Used for GitHub and Jules API.
*   **Download:** `HttpURLConnection` for raw file downloads (APKs).
