# Documentation Contradictions & Discrepancies Report

This report outlines contradictions found between the project's documentation, memory/instructions, and the actual codebase implementation.

## 1. "Repository-Less" / `min-app` Hallucination
*   **Documentation/Memory:** Several sources (memory, potential past instructions) refer to a `min-app` module that implements a "Repository-Less" architecture where no local Git clone exists.
*   **Reality:** The codebase contains a single module `:app`. It heavily utilizes `GitManager` (JGit) to perform local Git operations (commit, push, pull, fetch). The `BuildService` performs local builds on local files.
*   **Action:** Removed references to `min-app` and "Repository-Less" architecture. Confirmed `GitManager` usage.

## 2. "Post-Code" Philosophy vs. Implementation
*   **Documentation:** `README.md` states "This isn't no-code... The Post-Code IDE... User interacts primarily with their running application, not its source code." Memory claimed "code snippet retrieval logic has been removed."
*   **Reality:**
    *   `FileExplorerScreen.kt`: A fully functional file explorer.
    *   `FileContentScreen.kt`: A fully functional code editor using `rosemoe.sora.widget.CodeEditor`, allowing read/write access to source files.
    *   `CodeEditor.kt`: Contains `EnhancedCodeEditor` (Compose-based).
*   **Reconciliation:** The "Post-Code" philosophy remains the *primary* interaction model (Overlay, AI Prompting), but the Code Editor and File Explorer exist as "Developer Tools" or "Escape Hatches" (as correctly noted in `blueprint.md`).
*   **Action:** Updated documentation to reflect that these tools exist for power users but are not the intended primary workflow.

## 3. Overlay vs. Host Architecture
*   **Documentation:** `README.md`, `AGENTS.md`, and `architecture.md` describe an "Overlay" model where `IdeazOverlayService` (System Alert Window) floats over a running *system* application.
*   **Reality:**
    *   `MainScreen.kt` implements a **Host** architecture.
    *   **Android:** Uses `AndroidProjectHost` to launch the target app into a `VirtualDisplay` *inside* the IDE window.
    *   **Web:** Uses `WebProjectHost` (WebView) *inside* the IDE window.
    *   The "Overlay" (`SelectionOverlay`) is now a Composable layer drawn on top of the Host view within the app, not a System Alert Window over a background app.
    *   `IdeazOverlayService` still exists in the codebase but appears to be superseded by the Host model for the main workflow in `MainScreen`.
*   **Action:** Updated `blueprint.md` and `architecture.md` to describe the "Hybrid Host" architecture (VirtualDisplay/WebView) as the primary mechanism, with the Overlay Service possibly remaining for specific legacy or "undocked" use cases (though this is ambiguous in the code).

## 4. "No-Gradle" Build Pipeline
*   **Documentation:** `TODO.md` listed "No-Gradle" on Device as a goal.
*   **Reality:** `BuildService.kt` confirms this is implemented. It manually orchestrates `aapt2`, `kotlinc`, `d8` and uses `HttpDependencyResolver` to parse Gradle files and download dependencies without running Gradle itself.
*   **Action:** Verified and confirmed in documentation.

## 5. Project Structure
*   **Documentation:** `docs/file_descriptions.md` was outdated regarding the `ui/delegates` refactor and `ui/project` split.
*   **Reality:** `MainViewModel` is refactored into delegates. `ProjectScreen` is split into tabs.
*   **Action:** Updated `docs/file_descriptions.md` to match the current file structure.
