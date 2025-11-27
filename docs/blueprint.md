# Architectural Blueprint for IDEaz

## 1. Vision: The Visual, Post-Code IDE
IDEaz (com.hereliesaz.ideaz) is a mobile IDE that redefines app development. It is not a text editor; it is a **visual creation engine**. The user interacts primarily with their running application, not its source code.

*   **Core Loop:** Run App -> Visual Select -> AI Prompt -> AI Edit -> Compile -> Run.
*   **The "Invisible" Overlay:** The IDE provides a transparent overlay over the user's running app.
*   **Interaction:**
    *   **Select:** User taps a component or drags to select an area.
    *   **Prompt:** A text input appears near the selection.
    *   **Action:** User describes the change (e.g., "Make this button blue").
    *   **Execution:** The AI (Jules) accesses the source, implements changes, and the IDE rebuilds the app.

## 2. System Architecture

### 2.1 The "Race to Build" Strategy
To minimize user wait time, the IDE employs a dual-build strategy:
1.  **Local Build:** The device attempts to build the app using its internal toolchain (`aapt2`, `d8`, `kotlinc`).
2.  **Remote Build:** GitHub CI/CD is triggered to build the app remotely.

*   **Winner Takes All:** Whichever build finishes first (Local APK or Remote Release Artifact) is automatically downloaded/installed.
*   **Prioritization:**
    *   **Check Existing:** If the installed app matches the Repo Head (SHA), run it.
    *   **Check Remote:** If a compiled Release exists for the current SHA, download and install it.
    *   **Race:** If neither exists, start both Local and Remote builds.
*   **Local Priority:** The local build runs on a lower priority thread to ensure the UI remains responsive for the user to continue prompting/planning.

### 2.2 GitHub Integration
*   **Source of Truth:** GitHub is the backend. All changes are committed.
*   **AI Access:** Jules (the AI) works on the GitHub repository, pulling source, editing, and pushing commits.
*   **Synchronization:** The IDE automatically pulls changes from GitHub before building.

### 2.3 The Feedback Loop
*   **Success:** App updates live.
*   **Compilation Error:**
    *   If **User Code** fails: The build log is sent to Jules. Jules fixes the code, commits, and the cycle repeats.
    *   If **IDE Infrastructure** fails: The log is sent to the `HereLiesAz/IDEaz` repository as a GitHub Issue with the label `jules`. **Crucially, the user's AI is NOT asked to debug the IDE's own bugs.**

## 3. User Experience (UI/UX)

### 3.1 The Overlay
*   **Attachment:** The overlay "sticks" to the target application package. It is visible ONLY when the target app is in the foreground.
*   **Transparency:**
    *   **IDE Mode (Interact/Select):** Background is transparent.
    *   **Settings/Setup Screens:** Background is **Opaque** (Solid). Transparency here is a bug.
*   **Update Popup:** When a background build finishes, a popup ("Updating, gimme a sec") appears. Text in the prompt box is auto-copied to clipboard.

### 3.2 The Console (Bottom Card)
*   **Live Logs:** A swipe-up bottom card displays the live build log (Logcat) or AI output.
*   **Contextless Debugging:** Below the log is a prompt box for general questions or debugging assistance.

### 3.3 Background Reliability
*   **Notification:** A persistent notification displays the last 3 lines of the log, keeping the Build Service alive and the user informed even when the app is backgrounded.

## 4. Initialization vs. Loading

### 4.1 Loading
*   **Action:** User selects a project to "Load".
*   **Process:** Clone repo, Sync changes, Fetch branches.
*   **Destination:** Takes user to the **Setup Tab**, NOT the build screen.

### 4.2 Initialization
*   **Action:** User presses "Save & Initialize" in the Setup Tab.
*   **Process:**
    1.  **Inject Workflows:** Force-push critical workflow files (`android_ci_jules.yml`, `codeql.yml`, `jules.yml`, `release.yml`) to `.github/workflows/`.
    2.  **Inject Scripts:** Force-push `setup_env.sh` and `AGENTS_SETUP.md`.
    3.  **Start Build:** Initiate the first build/run cycle.

## 5. Agent Constraints
*   **Polling:** Polling for AI progress must **NEVER** time out. Agents need time to think and setup. Poll for *activities*, not just the final result.
*   **Environment:** The user never touches a file. The AI handles the file system.
