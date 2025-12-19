# Task Flows

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
    *   **Starts Build:** Initiates the "Race to Build".

## 2. The "Race to Build"
To minimize wait time, the IDE races a local build against a remote one.

1.  **Check Installed:** If `InstalledSHA == RepoHeadSHA`, launch app immediately.
2.  **Check Remote:** If `ReleaseSHA == RepoHeadSHA`, download and install immediately.
3.  **Race Start:** If neither matches:
    *   **Remote:** Trigger GitHub Action (via push).
    *   **Local:** Start background build (low priority).
        *   *Note:* For `WEB` projects, the local build generates `index.html`, and a push triggers remote deployment (Pages).
4.  **Finish:**
    *   **Local Wins:** Install local APK. Cancel remote job (optional).
    *   **Remote Wins:** Download release APK. Cancel local job.
5.  **Update:** Show "Updating, gimme a sec" popup. Copy prompt text to clipboard. Reload app.

## 3. The Core Development Loop
1.  **Select:** User selects element/area on the Overlay.
2.  **Prompt:** User inputs instruction.
3.  **AI Processing:**
    *   Jules reads source.
    *   Jules commits changes to GitHub.
4.  **Sync:** IDE pulls changes.
5.  **Rebuild:** "Race to Build" restarts.

## 4. The Error Handling Loop

### 4.1 User Code Error
*   **Context:** Compilation fails due to syntax/logic error.
*   **Action:**
    *   Capture Build Log.
    *   Send to **User's Active AI Session**.
    *   Jules fixes code -> Commit -> IDE Pulls -> Rebuild.

### 4.2 IDE Infrastructure Error
*   **Context:** IDE crashes, Tool missing, API failure.
*   **Action:**
    *   Capture Stack Trace.
    *   **Report to:** `HereLiesAz/IDEaz` (The IDE's own repo).
    *   **Label:** `jules` (Triggers the IDE team's debugging agent).
    *   **Constraint:** DO NOT send this to the user's session.

## 5. Background Reliability
*   **Notification:** Persistent notification shows last 3 log lines.
*   **Polling:** AI polling loop **never times out**. It checks for *Activity* updates to show liveness.
