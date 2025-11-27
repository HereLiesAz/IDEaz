# Screen Definitions

## 1. Main Screen (`MainScreen.kt`)
*   **Role:** The root container.
*   **Components:**
    *   `IdeNavRail`: Navigation bar.
    *   `IdeNavHost`: Handles navigation between screens.
    *   `IdeBottomSheet`: Persistent bottom sheet for logs/chat.
    *   `LiveOutputBottomCard`: Floating status card.
    *   `ContextlessChatInput`: Input for global chat.

## 2. Project Screen (`ProjectScreen.kt`)
*   **Role:** Project management.
*   **Tabs:**
    *   **Load:** List local projects.
    *   **Clone:** List/Search GitHub repositories.
    *   **Create:** Create new projects from templates.
    *   **Setup:** Configure the current project (Sessions, Environment).
*   **State:** Controlled by `MainViewModel`.

## 3. Editor Screen (`FileExplorerScreen.kt`, `FileContentScreen.kt`)
*   **Role:** Read-only file browsing.
*   **Components:**
    *   `FileExplorerScreen`: Tree view of files.
    *   `FileContentScreen`: Uses `CodeEditor` (Rosemoe) to display content.

## 4. Git Screen (`GitScreen.kt`)
*   **Role:** Version control interface.
*   **Features:**
    *   Branch list (tree view).
    *   Commit history.
    *   Status (changed files).
    *   Actions: Commit, Push, Pull, Fetch, Stash, Checkout.

## 5. Settings Screen (`SettingsScreen.kt`)
*   **Role:** Configuration.
*   **Sections:**
    *   **AI:** API Keys, Model selection.
    *   **GitHub:** User, Token.
    *   **Appearance:** Theme, Log Verbosity.
    *   **Tools:** Keystore management.
    *   **System:** Permissions check.

## 6. Dependency Screen (`LibrariesScreen.kt`)
*   **Role:** Manage project dependencies.
*   **Features:**
    *   List declared dependencies.
    *   Show resolve status (Success/Fail).
    *   Add new dependency (Wizard).

## 7. Web Runtime (`WebRuntimeActivity.kt`)
*   **Role:** Hosts the built Web project.
*   **Component:** `WebView` loading the generated `index.html`.
