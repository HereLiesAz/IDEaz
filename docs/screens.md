# Screen Definitions

## 1. The Overlay ("Invisible Screen")
*   **Role:** The primary interface for "Post-Code" development.
*   **Implementation:** `UIInspectionService` (Accessibility Service).
*   **Context:** Visible *only* over the target application.
*   **Components:**
    *   **Selection Highlight:** Visual border around selected nodes or drawn rects.
    *   **Prompt Input:** Floating text box anchored to the selection.
    *   **Update Popup:** "Updating, gimme a sec" toast/dialog.

## 2. Main Host Screen (`MainScreen.kt`)
*   **Role:** The container for the IDE management UI.
*   **Components:**
    *   `IdeNavRail`: Navigation bar (Project, Git, Settings).
    *   `IdeBottomSheet`: The Global Console.
    *   `LiveOutputBottomCard`: Floating status indicator.

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
    *   **Load:** Select existing local project. -> **Transitions to Setup Tab.**
    *   **Clone:** Search/Clone from GitHub.
    *   **Create:** Generate from template.
    *   **Setup:** **INITIALIZATION happens here.**
        *   Displays Sessions.
        *   "Save & Initialize" button triggers workflow injection and first build.

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
*   **Sections:** AI Keys, GitHub Token, Theme, Tool Management.

## 7. Web Runtime (`WebRuntimeActivity.kt`)
*   **Role:** Host for Web projects.
*   **Component:** `WebView` loading the generated `index.html`.
