# Screen Definitions

## 1. The Overlay ("Invisible Screen")
*   **Role:** The primary interface for "Post-Code" development.
*   **Implementation:** `UIInspectionService` (Accessibility Service) + `BubbleActivity`.
*   **Context:** Visible *only* over the target application.
*   **Modes:**
    *   **Interact:** Pass-through to target app.
    *   **Select:** Blocks interaction to allow Element Tap or Rect Drag selection.
*   **Components:**
    *   **Selection Highlight:** Visual border around selected nodes or drawn rects (managed by `UIInspectionService`).
    *   **Contextual Chat:** Inline chat display + input anchored to the selection (managed by `BubbleActivity`).
    *   **Update Popup:** "Updating, gimme a sec" toast/dialog.

## 2. Main Host Screen (`MainScreen.kt`)
*   **Role:** The container for the IDE management UI (Docked Mode).
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
    *   **Load:** Select existing local project. **Includes "Import Project Folder" button for external projects.** -> **Transitions to Setup Tab.**
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

## 8. Bubble Overlay (`BubbleActivity.kt`)
*   **Role:** Provides a persistent, system-managed overlay for quick access to IDE features while using other apps. The "IDE Screen".
*   **Implementation:** Android Bubbles API via `BubbleActivity`.
*   **Components:**
    *   `IdeNavRail`: Navigation bar.
    *   `IdeNavHost`: Content area for IDE screens within the bubble.
    *   `ContextualChatOverlay`: The chat interface displayed over a selection.
