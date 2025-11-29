# Task Flows (Min-App)

## 1. Project Lifecycle

### 1.1 Loading (Preparation)
1.  **User Action:** Selects a project in "Load" tab.
2.  **System:**
    *   Clones/Pulls repository.
    *   Detects Project Type.
    *   **Navigates to Setup Tab.** (Does NOT start build).

### 1.2 Initialization (Activation)
1.  **User Action:** Clicks "Save & Initialize" in Setup Tab.
2.  **System:**
    *   **Injects Workflows:** Force-pushes `android_ci_jules.yml`, `codeql.yml`, `jules.yml`, `release.yml`.
    *   **Injects Scripts:** Force-pushes `setup_env.sh`, `AGENTS_SETUP.md`.
    *   **Starts Build:** Initiates the Remote Build via Workflow Dispatch.

## 2. The Remote Build Lifecycle
The IDE relies solely on GitHub Actions for building.

1.  **Check Installed:** If `InstalledSHA == RepoHeadSHA`, launch app immediately.
2.  **Check Remote:** If `ReleaseSHA == RepoHeadSHA`, download and install immediately.
3.  **Build Start:** If neither matches:
    *   **Trigger:** Dispatch GitHub Action or push triggers workflow.
    *   **Monitor:** Poll GitHub Actions API for progress.
4.  **Finish:**
    *   **Success:** Download release APK from GitHub Release assets.
    *   **Failure:** Capture logs for AI debugging.
5.  **Update:** Show "Updating, gimme a sec" popup. Copy prompt text to clipboard. Reload app.

## 3. The Core Development Loop
1.  **Select:** User selects element/area on the Overlay.
2.  **Prompt:** User inputs instruction.
3.  **AI Processing:**
    *   Jules reads source.
    *   Jules commits changes to GitHub.
4.  **Sync:** IDE pulls changes (for visualization/status only).
5.  **Rebuild:** Remote Build Lifecycle restarts.

## 4. The Error Handling Loop

### 4.1 User Code Error
*   **Context:** Remote compilation fails due to syntax/logic error.
*   **Action:**
    *   Capture Remote Build Log (via GitHub API).
    *   Send to **User's Active AI Session**.
    *   Jules fixes code -> Commit -> Rebuild.

### 4.2 IDE Infrastructure Error
*   **Context:** IDE crashes, API failure (GitHub/Jules).
*   **Action:**
    *   Capture Stack Trace.
    *   **Report to:** `HereLiesAz/IDEaz` (The IDE's own repo).
    *   **Label:** `jules` (Triggers the IDE team's debugging agent).
    *   **Constraint:** DO NOT send this to the user's session.

## 5. Background Reliability
*   **Notification:** Persistent notification shows the status of the remote build and download progress.
*   **Polling:** AI and GitHub polling loops **never time out**. They check for updates to show liveness.
