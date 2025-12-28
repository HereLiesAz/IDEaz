# IDEaz: Architecture

## 1. The Core Loop
The IDE is designed as a **Hybrid Host & Overlay**. It hosts the user's running app internally or overlays it.

1.  **User Interacts:** The user uses their app (hosted within `AndroidProjectHost` or `WebProjectHost`).
2.  **User Selects:** The user drags a box over an area they want to change using the `SelectionOverlay`.
3.  **User Prompts:** The user types "Make this button blue".
4.  **IDE Acts:**
    *   Finds the relevant source code (via `ProjectAnalyzer` or Source Maps).
    *   Sends the prompt + code + context to the AI (Jules/Gemini).
    *   Receives a diff/patch.
    *   Applies the patch via `GitManager`.
    *   Triggers a background build via `BuildService`.
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
*   **`UpdateDelegate`:** Checks for self-updates.
*   **`StateDelegate`:** Holds the mutable state variables.

## 3. The Services
*   **`BuildService` (Execution Layer):** A **Foreground Service** running in a separate process (`:build_process`). It orchestrates the entire build toolchain (aapt2, kotlinc, d8) and handles APK installation to prevent UI freezes in the main app.
*   **`IdeazOverlayService` (Visual Layer - Legacy/System):** A **Foreground Service** running as a `TYPE_APPLICATION_OVERLAY` window. While the main UI now uses `MainScreen`'s internal composition for overlays (`SelectionOverlay`), this service remains for system-level overlay capabilities and potentially for un-docked interactions.
*   **`IdeazAccessibilityService` (Inspection Layer):** An **Accessibility Service** that retrieves `AccessibilityNodeInfo` from the window hierarchy. It allows the IDE to "see" the UI elements under a user's tap or drag selection.
*   **`CrashReportingService` (Safety Layer):** A dedicated service running in its own process (`:crash_reporter`) to ensure fatal crashes are reported to the API even if the main app dies.

## 4. Data Flow
*   **State:** UI components observe `MainViewModel.state` (which delegates to `StateDelegate`).
*   **Events:** UI calls `MainViewModel.action()`, which delegates to `Delegate.action()`.
*   **Updates:** Delegates update `StateDelegate` variables, which emit new `StateFlow` values.

## 5. File System
*   **Projects:** Stored in `context.filesDir/projects/`.
*   **Imports:** Copied from `content://` URI to internal storage (`filesDir/projects/Imported_Project_Name`). Direct editing of external files (SAF) is not supported for performance and permission reasons.
*   **Tools:** Downloaded to `context.filesDir/local_build_tools/`.
*   **Temp:** `context.cacheDir` used for download buffers.

## 6. Networking
*   **Retrofit:** Used for GitHub and Jules API.
*   **Download:** `HttpURLConnection` for raw file downloads (APKs, Tools).
*   **Interceptors:** `AuthInterceptor` injects keys; `LoggingInterceptor` sanitizes logs.
