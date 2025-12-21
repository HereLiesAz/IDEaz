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
*   **`OverlayDelegate`:** Manages the visual overlay state and selection logic.
*   **`SystemEventDelegate`:** Handles broadcast receivers (Package install, etc).
*   **`UpdateDelegate`:** Checks for IDEaz self-updates.
*   **`StateDelegate`:** Holds the mutable state variables.

## 3. The Services
*   **`IdeazOverlayService` (Target):** The UI Overlay and "Main Window" of the IDE. Designed to extend `AzNavRailOverlayService` (v5.2+) to provide a dynamically sized system alert window.
    *   *Note: Current implementation status is flux. Codebase may reflect a transition period where this service is being refactored or is temporarily disabled in favor of activity-based hosting for debugging.*
*   **`BuildService`:** A background (foreground) service that runs Gradle tasks and APK installation. It runs in a separate process (`:build_process`) to prevent UI freezes.
*   **`CrashReportingService`:** Catches and reports fatal crashes. Runs in a separate process (`:crash_reporter`).
*   **`UIInspectionService` / `IdeazAccessibilityService`:** An Accessibility Service used to retrieve Node Information (`AccessibilityNodeInfo`) for element selection and highlighting.

## 4. Data Flow
*   **State:** UI components observe `MainViewModel.state` (which delegates to `StateDelegate`).
*   **Events:** UI calls `MainViewModel.action()`, which delegates to `Delegate.action()`.
*   **Updates:** Delegates update `StateDelegate` variables, which emit new `StateFlow` values.

## 5. File System
*   **Projects:** Stored in `context.filesDir/projects/`.
*   **Imports:** Copied from `content://` URI to internal storage (`filesDir/projects/Imported_Project_Name`). Direct editing of external files (SAF) is not supported for performance and permission reasons.
*   **Temp:** `context.cacheDir` used for download buffers.

## 6. Networking
*   **Retrofit:** Used for GitHub and Jules API.
*   **Download:** `HttpURLConnection` for raw file downloads (APKs, Tools).
*   **Interceptors:** `AuthInterceptor` injects keys; `LoggingInterceptor` sanitizes logs.
