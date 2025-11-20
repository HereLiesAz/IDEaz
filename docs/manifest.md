## IDEaz UI Component Manifest: Complete Deconstruction (Revised)

### I. Host Application Shell (MainScreen Structural Components)

* **Scaffold**
    -   **Appearance:** The root Compose container, configured with a `containerColor = Color.Transparent`.
    -   **Function:** Serves as the base layout structure for the application UI.
    -   **Conditions:** Always present and fills the screen.
* **IdeNavHost**
    -   **Appearance:** Occupies the main, weighted screen space adjacent to the NavRail.
    -   **Function:** Displays the active application screen (e.g., Settings, Project Screen, Main IDE).
    -   **Conditions:** **`AnimatedVisibility`** is used, and it is **visible** (`isIdeVisible = true`) when the Bottom Sheet is *not* at `AlmostHidden`, OR when the current route is "settings" or "project\_settings".
* **IdeBottomSheet (Global Console)**
    -   **Appearance:** A pull-up card with defined detents: `AlmostHidden`, `Peek`, and `Halfway`.
    -   **Function:** Provides a **consolidated global log** for output such as build status, AI status, compile logs, and general chat history.
    -   **Conditions:** **Visible** (`isBottomSheetVisible = true`) only when the current navigation route is "main" or "build".
* **Contextless Chat Input**
    -   **Appearance:** A text input field aligned to the bottom center of the screen.
    -   **Function:** Used to send global AI prompts that are not bound to a specific UI element.
    -   **Conditions:** **Visible** (`isChatVisible = true`) when the bottom sheet is at the `Peek` or `Halfway` detent.

---

### II. NavRail Components (IdeNavRail)

* **Project Rail Item (Renamed Project Screen)**
    -   **Appearance:** Button labeled "Project".
    -   **Function:** Navigates to the Project Screen (`route = "project_settings"`).
* **IDE Rail Host Item**
    -   **Appearance:** Button labeled "IDE".
    -   **Function:** Navigates to the Main IDE Screen (`route = "main"`).
* **Prompt Sub Item (Legacy)**
    -   **Appearance:** Button labeled "Prompt" (nested under "IDE").
    -   **Function:** Triggers the outdated `PromptPopup` modal.
* **Build Sub Item**
    -   **Appearance:** Button labeled "Build" (nested under "IDE").
    -   **Function:** Navigates to the Build screen (`route = "build"`) and moves the bottom sheet to the `Halfway` detent.
* **Mode Toggle (azRailSubToggle)**
    -   **Appearance:** Button labeled **"Interact"** (Selection Mode) or **"Select"** (Interaction Mode).
    -   **Function:** Toggles between **Interaction Mode** (app is interactive, `UIInspectionService` stopped) and **Selection Mode** (app is intercepted, `UIInspectionService` started).
* **Settings Rail Item**
    -   **Appearance:** Button labeled "Settings".
    -   **Function:** Navigates to the Settings screen (`route = "settings"`).

---

### III. Selection Mode Overlays (UI Inspection Service Components)

* **UI Inspection Overlay (Touch Interceptor)**
    -   **Appearance:** A transparent layer over the running target application.
    -   **Function:** Intercepts all touch events for selection (tapping an element or dragging an area).
    -   **Conditions:** Active only in **Selection Mode** (sheet up, `UIInspectionService` running).
* **Contextual Log Overlay**
    -   **Appearance:** A semi-transparent floating UI window sized to match the user's current selection bounds.
    -   **Function:** Streams the AI's chat and work output for the contextual task.
    -   **Conditions:** Appears **after** a contextual prompt has been submitted.
* **Cancel Button (X)**
    -   **Appearance:** A button in the top-right corner of the Contextual Log Overlay.
    -   **Function:** Cancels the current AI task and triggers the confirmation dialog.
* **Contextual Prompt Input**
    -   **Appearance:** A text input box that appears directly below the Log Overlay.
    -   **Function:** Allows the user to type and submit an instruction bound to the selected UI element.
    -   **Conditions:** Appears **after** a visual selection is made and disappears upon prompt submission.

---

### IV. Navigated Content Screens (Within IdeNavHost)

* **Project Screen (`route = "project_settings"`)**
    -   **Function:** The central screen for managing mobile app projects, organized by tabs.
    -   **Structure:**
        * **Setup Tab (replaces "Create"):** Contains fields that govern the **Android app setup**, such as the App Name.
        * **Clone Tab:** Displays a list of **repositories available** to the user, with the list obtained via a query to **Jules**.
        * **Load Tab:** Displays a list of **projects that have already been cloned** to the device.
* **Main IDE Screen (`route = "main"`)**
    -   **Appearance:** Placeholder `Text("Main IDE Screen")` centered in the content area.
    -   **Function:** Serves as the default view, currently empty as status logs were relocated to the Bottom Sheet.
* **Build Screen (`route = "build"`)**
    -   **Appearance:** (Implied screen content).
    -   **Function:** Displays detailed build status and logs.
* **Settings Screen (`route = "settings"`)**
    -   **Section Header: API Keys**
        -   **Jules API Key Input:** `AzTextBox` for the key and "Get Key" button.
        -   **AI Studio API Key Input:** `AzTextBox` for the key and "Get Key" button.
    -   **Section Header: AI Assignments**
        -   **AI Assignment Dropdowns:** Multiple dropdowns to assign specific AI models to specific tasks.
    -   **Section Header: Permissions**
        -   **Permission Check Rows:** List of system permissions (e.g., Draw Over Other Apps) with a label and a **disabled** `Switch` if granted.
    -   **Section Header: Preferences**
        -   **Cancel Warning Checkbox:** Toggles the confirmation dialog for canceling AI tasks.
        -   **Theme Dropdown:** Sets the application's theme mode.
    -   **Section Header: Log Level**
        -   **Log Level Dropdown:** Sets logging verbosity (Info, Debug, Verbose).
    -   **Section Header: Debug**
        -   **Clear Build Caches Button:** Clears internal build caches.

---

### VI. Ephemeral Popups

* **Cancel Task Dialog**
    -   **Appearance:** `AlertDialog` titled "Cancel Task" with a message about lost progress and "Confirm"/"Dismiss" buttons.
    -   **Function:** Confirms cancellation of an active AI task.
* **Prompt Popup (Legacy)**
    -   **Appearance:** A dialog for text input.
    -   **Function:** Submits a general prompt (triggered by NavRail sub-item).