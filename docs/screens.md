# Screen Definitions

## 1. The Overlay ("Invisible Screen")
*   **Role:** The primary interface for "Post-Code" development.
*   **Implementation:** `IdeazAccessibilityService` (Accessibility Service for Inspection) + `IdeazOverlayService` (Foreground Service for UI Window).
*   **Context:** Visible *only* over the target application.
*   **Modes:**
    *   **Interact:** Pass-through to target app.
    *   **Select:** Blocks interaction to allow Element Tap or Rect Drag selection.
*   **Components:**
    *   **Selection Highlight:** Visual border around selected nodes or drawn rects (managed by `IdeazOverlayService`).
    *   **Contextual Chat:** Inline chat display + input anchored to the selection (managed by `IdeazOverlayService`).
    *   **Update Popup:** "Updating, gimme a sec" toast/dialog.

## 2. Main Host Screen (`MainScreen.kt`)
*   **Role:** The container for the IDE management UI (Docked Mode) and the Embedded App Host.
*   **Components:**
    *   `IdeNavRail`: Navigation bar (Project, Git, Settings, Files, Libs).
    *   `IdeBottomSheet`: The Global Console.
    *   `LiveOutputBottomCard`: Floating status indicator.
    *   `AndroidProjectHost`: Hosts the target Android app in a Virtual Display (replaces pure Overlay for some use cases).
    *   `WebProjectHost`: Hosts Web projects in a WebView (integrated as bottom layer).

## 3. The Global Console (`IdeBottomSheet`)
*   **Role:** Visibility into background processes.
*   **States:**
    *   **Hidden:** User is interacting with the app.
    *   **Peek:** Shows status summary.
    *   **Expanded:** Shows full logs.
*   **Content Modes:**
    *   **Git Terminal:** Output from `GitManager` operations.
    *   **Build Log:** Live stream from `BuildService`.
    *   **AI Log:** Real-time activity stream from Jules/Gemini.
    *   **Debug Chat:** Contextless AI prompt input via `ContextlessChatInput`.

## 4. Project Screen (`ProjectScreen.kt`)
*   **Role:** Entry point.
*   **Tabs:**
    *   **Setup:** **INITIALIZATION happens here.**
        *   Displays Sessions.
        *   "Create" mode allows generating from template.
        *   "Save & Initialize" button triggers workflow injection and first build.
    *   **Load:** Select existing local project. **Includes "Add External Project" / "Grant Storage Permission" button below the list.** -> **Transitions to Setup Tab.**
    *   **Clone:** Search/Clone from GitHub.

## 5. Git Screen (`GitScreen.kt`)
*   **Role:** Version control management.
*   **Features:**
    *   Branch Tree View.
    *   Commit History.
    *   Stash/Unstash controls.
    *   Force Update Init Files (Menu option).

## 6. Developer Tools (Auxiliary)
These screens are for low-level debugging and management, bypassing the "Post-Code" abstraction when necessary.

*   **File Explorer (`FileExplorerScreen.kt`):**
    *   **Role:** Direct filesystem access to the project directory.
    *   **Features:** Navigate directories, open files.
    *   **Context:** Useful for verifying generated code or assets.
*   **File Viewer (`FileContentScreen.kt`):**
    *   **Role:** Read-only (or limited edit) view of file content with syntax highlighting.
*   **Dependency Manager (`LibrariesScreen.kt`):**
    *   **Role:** Manage project dependencies.
    *   **Features:** View installed libraries, check for updates, view failed dependency errors.

## 7. Settings Screen (`SettingsScreen.kt`)
*   **Role:** Configuration.
*   **Visuals:** **Opaque Background** (Transparency is not allowed here).
*   **Sections:**
    1.  **Build Configuration:** (Local vs Cloud builds).
    2.  **Saved Settings & Credentials:** Export/Import encrypted settings.
    3.  **Signing Configuration:** Keystore management.
    4.  **API Keys:** Jules, GitHub, AI Studio.
    5.  **AI Assignments:** Map tasks to AI models.
    6.  **Permissions:** System permission status/requests.
    7.  **Preferences/Theme/Logs/Updates/Debug.**

## 8. Web Runtime (Embedded in `MainScreen.kt`)
*   **Role:** Host for Web projects.
*   **Component:** `WebProjectHost` (WebView) integrated directly into `MainScreen` as the bottom layer.
