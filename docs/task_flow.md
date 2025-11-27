# Task Flows

## 1. Onboarding & Project Setup
1.  **Launch App:** User grants permissions.
2.  **Settings:** User enters API keys (GitHub, Google, Jules).
3.  **Project Screen:**
    *   **Clone:** User selects a repo from GitHub.
    *   **Load:** Repository is cloned/pulled.
    *   **Initialize:** App checks for `setup_env.sh` and workflows. If missing, injects and pushes them.
4.  **First Build:** App automatically triggers a build (`startBuild`).

## 2. The "Select & Edit" Loop (Core Loop)
1.  **Interact:** User uses the app in "Interact Mode".
2.  **Select:** User switches to "Select Mode" (Overlay active).
3.  **Tap/Drag:** User selects an element.
4.  **Prompt:** User describes change in the prompt box.
5.  **Process:**
    *   `UIInspectionService` sends prompt + context to `MainViewModel`.
    *   `MainViewModel` calls AI (Jules/Gemini).
    *   AI generates a Git patch.
    *   `MainViewModel` applies patch.
6.  **Rebuild:** `MainViewModel` triggers `BuildService`.
7.  **Reload:** App reloads with changes.

## 3. The "Fix It" Loop (Bug Report)
1.  **Crash:** App crashes or build fails.
2.  **Report:** `MainViewModel` captures the error.
3.  **Analyze:** If it's an internal IDE error, it's reported to GitHub.
4.  **AI Fix:** If it's a user error, the "AI Debugger" kicks in (optional contextless chat) to suggest fixes.

## 4. Git Management
1.  **Status:** User checks `GitScreen`.
2.  **Commit:** User stages and commits changes.
3.  **Push:** User pushes to remote.
