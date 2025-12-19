# Developer Workflow & Logic

## 1. The "Race to Build" Strategy
IDEaz minimizes user wait time by racing a local build against a remote CI build.

### Decision Logic
1.  **Check Installed:** Is the app installed? Does its SHA match the Repo HEAD?
    *   *Yes:* Run Installed App.
2.  **Check Remote:** Is there a GitHub Release for the current HEAD SHA?
    *   *Yes:* Download and Install.
3.  **Race:** If neither:
    *   **Start Remote:** Trigger GitHub Action (by push).
    *   **Start Local:** Start on-device build (low priority thread).
    *   **Winner:** First to finish triggers installation. The loser is cancelled (or ignored).

## 2. Project Lifecycle: Loading vs. Initialization

### 2.1 Loading (Preparation)
*   **Trigger:** User selects a project from the "Load" tab.
*   **Actions:**
    1.  Clone/Pull repository.
    2.  Fetch branches and history.
    3.  Detect Project Type.
    4.  Navigate to **Setup Tab**.
*   **Note:** This does *not* start a build.

### 2.2 Initialization (Activation)
*   **Trigger:** User clicks "Save & Initialize" in Setup Tab.
*   **Actions:**
    1.  **Inject Workflows:** The IDE *must* force-push the following files to `.github/workflows/`:
        *   `android_ci_jules.yml`
        *   `codeql.yml`
        *   `jules.yml`
        *   `release.yml`
    2.  **Inject Environment:** Force-push `setup_env.sh` and `AGENTS_SETUP.md` to root.
    3.  **Start Build:** Initiate the "Race to Build".

## 3. The Error Handling Loop
The IDE distinguishes between "User Errors" (code that won't compile) and "IDE Errors" (the toolchain crashed).

### Scenario A: User Code Error
*   **Detection:** `BuildService` returns failure, but `isIdeError()` (heuristic) returns false.
*   **Action:**
    1.  Capture Build Log.
    2.  Send to **User's AI Session** (Jules).
    3.  AI fixes code, commits, pushes.
    4.  IDE pulls and retries build.

### Scenario B: IDE Infrastructure Error
*   **Detection:** `BuildService` throws Exception or `isIdeError()` returns true (e.g., "Tool not found").
*   **Action:**
    1.  Capture Stack Trace + Context.
    2.  Report to **IDEaz Repository** (`HereLiesAz/IDEaz`) as a GitHub Issue.
    3.  **Label:** Must use label `jules` to trigger the debugging workflow.
    4.  **Constraint:** Do *not* ask the user's AI to fix this.

## 4. Working with Agents (Jules)
*   **Polling:** Polling for AI responses should **never time out**. Agents need time. Poll for `activities` to show progress.
*   **Transparency:** Always show the "Live Output" card when waiting for AI or Build.
