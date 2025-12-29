# Documentation Contradictions & Discrepancies Report

This report outlines contradictions found between the project's documentation, memory/instructions, and the actual codebase implementation.

## 1. "Repository-Less" / `min-app` Hallucination
*   **Documentation/Memory:** Several sources (memory, potential past instructions) refer to a `min-app` module that implements a "Repository-Less" architecture where no local Git clone exists.
*   **Reality:** The codebase contains a single module `:app`. It heavily utilizes `GitManager` (JGit) to perform local Git operations (commit, push, pull, fetch). The `BuildService` performs local builds on local files.
*   **Action:** Removed references to `min-app` and "Repository-Less" architecture. Confirmed `GitManager` usage.

## 2. "Post-Code" Philosophy vs. Implementation
*   **Documentation:** `README.md` stated "This isn't no-code... The Post-Code IDE... User interacts primarily with their running application, not its source code." Memory claimed "code snippet retrieval logic has been removed."
*   **Reality:**
    *   `FileExplorerScreen.kt`: A fully functional file explorer.
    *   `FileContentScreen.kt`: A fully functional code editor using `rosemoe.sora.widget.CodeEditor`, allowing read/write access to source files.
    *   `CodeEditor.kt`: Contains `EnhancedCodeEditor` (Compose-based).
*   **Resolution:** The `README.md` has been updated to explicitly acknowledge these tools as "Auxiliary Tools" or "Escape Hatches" for power users/debugging, reconciling the philosophy with the implementation.
*   **Status:** Resolved in Documentation.

## 3. Overlay vs. Host Architecture
*   **Documentation:** `README.md`, `AGENTS.md`, and `architecture.md` described an "Overlay" model where `IdeazOverlayService` (System Alert Window) floats over a running *system* application.
*   **Reality:**
    *   `MainScreen.kt` implements a **Host** architecture.
    *   **Android:** Uses `AndroidProjectHost` to launch the target app into a `VirtualDisplay` *inside* the IDE window.
    *   **Web:** Uses `WebProjectHost` (WebView) *inside* the IDE window.
    *   The "Overlay" (`SelectionOverlay`) is now a Composable layer drawn on top of the Host view within the app, not a System Alert Window over a background app.
    *   `IdeazOverlayService` still exists but is secondary.
*   **Resolution:** `README.md` and `architecture.md` have been updated to describe the "Hybrid Host" architecture as primary.
*   **Status:** Resolved in Documentation.

## 4. "No-Gradle" Build Pipeline
*   **Documentation:** `TODO.md` listed "No-Gradle" on Device as a goal.
*   **Reality:** `BuildService.kt` confirms this is implemented. It manually orchestrates `aapt2`, `kotlinc`, `d8` and uses `HttpDependencyResolver` to parse Gradle files and download dependencies without running Gradle itself.
*   **Status:** Verified Implemented.

## 5. Project Structure
*   **Documentation:** `docs/file_descriptions.md` was outdated regarding the `ui/delegates` refactor and `ui/project` split.
*   **Reality:** `MainViewModel` is refactored into delegates. `ProjectScreen` is split into tabs.
*   **Resolution:** `docs/file_descriptions.md` has been updated.
*   **Status:** Resolved in Documentation.

## 6. Project Screen "Create" Tab
*   **Documentation:** Previous docs might imply a separate "Create" tab.
*   **Reality:** The "Create" functionality is now a state within the `SetupTab` (`ProjectSetupTab.kt`), toggled by an `isCreateMode` boolean. The explicit `CreateTab.kt` file might be unused or merged. `ProjectScreen.kt` tab list is `["Setup", "Load", "Clone"]`.
*   **Status:** Verified (Updated in `docs/screens.md`).
