# Screen Definitions

## 1. The Overlay ("Invisible Screen")
*   **Role:** The primary interface for "Post-Code" development.
*   **Implementation Status:** **Partially Broken / Under Refactor.**
    *   The `IdeazOverlayService` (system alert window) is currently **missing** from the codebase.
    *   `IdeazAccessibilityService` exists but contains no logic.
    *   `OverlayDelegate` handles selection state and broadcasts, but there is no active receiver to draw the highlights over external apps.
*   **Current Behavior:** Overlay features (Contextual Chat, Selection) currently only function effectively within the internal `AndroidProjectHost` or `WebProjectHost` where Compose can render them.
*   **Intended Components:**
    *   **Selection Highlight:** Visual border around selected nodes or drawn rects.
    *   **Contextual Chat:** Inline chat display + input anchored to the selection.

## 2. Main Host Screen (`MainScreen.kt`)
*   **Role:** The container for the IDE management UI (Docked Mode) and the Embedded App Host.
*   **Components:**
    *   `IdeNavRail`: Navigation bar (Project, Git, Settings).
    *   `IdeBottomSheet`: The Global Console.
    *   `AndroidProjectHost`: Hosts the target Android app in a Virtual Display (replaces pure Overlay for some use cases).
    *   `WebProjectHost`: Hosts Web projects in a WebView.

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
    *   **Debug Chat:** Contextless AI prompt input.

## 4. Project Screen (`ProjectScreen.kt`)
*   **Role:** Entry point.
*   **Tabs:**
    1.  **Setup:** **INITIALIZATION happens here.**
        *   Displays Sessions.
        *   "Save & Initialize" button triggers workflow injection and first build.
    2.  **Load:** Select existing local project. **Includes "Add External Project" / "Grant Storage Permission" button.**
    3.  **Clone:** Search/Clone from GitHub.

## 5. Git Screen (`GitScreen.kt`)
*   **Role:** Version control management.
*   **Features:**
    *   Branch Tree View.
    *   Commit History.
    *   Stash/Unstash controls.
    *   Force Update Init Files (Menu option).

## 6. Settings Screen (`SettingsScreen.kt`)
*   **Role:** Configuration.
*   **Visuals:** **Opaque Background** (Transparency is not allowed here).
*   **Sections Order:**
    1.  **Build Configuration:** (Local vs Cloud builds).
    2.  **Saved Settings & Credentials:** Export/Import encrypted settings.
    3.  **Signing Configuration:** Keystore management.
    4.  **API Keys:** Jules, GitHub, AI Studio.
    5.  **AI Assignments:** Map tasks to AI models.
    6.  **Permissions:** System permission status/requests.
    7.  **Preferences:** Cancel warnings, Auto-report bugs, etc.
    8.  **Theme:** Dark/Light/Auto.
    9.  **Log Level:** Info/Debug/Verbose.
    10. **Updates:** Check for updates.
    11. **Debug:** Clear caches.

## 7. Web Runtime (Embedded in `MainScreen.kt`)
*   **Role:** Host for Web projects.
*   **Component:** `WebProjectHost` (WebView) integrated directly into `MainScreen` as the bottom layer.
