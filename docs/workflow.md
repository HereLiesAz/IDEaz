# Workflow Documentation

This document outlines the operational workflows within IDEaz.

## 1. The "Race to Build"
IDEaz employs a competitive build strategy to ensure the user always has the latest version.

1.  **Trigger:** User saves changes or Git pushes.
2.  **Remote Branch:** CI (GitHub Actions) starts building the APK.
3.  **Local Branch:** `BuildService` starts building the APK on the device.
4.  **Race:**
    *   If Remote finishes first -> `UpdateDelegate` detects the new Release asset -> Prompts user to Update.
    *   If Local finishes first -> `ApkInstaller` installs the local APK.
5.  **Synchronization:** If Local wins, it pushes the changes to Remote to ensure CI catches up.

## 2. Project Initialization
1.  **User Input:** Selects Template (Android, Web, etc.) and Name.
2.  **Generation:** `RepoDelegate` copies assets to `filesDir/{Name}`.
3.  **Injection:** `ProjectConfigManager` writes:
    *   `android_ci_jules.yml` (CI Workflow)
    *   `setup_env.sh` (Environment Script)
    *   `AGENTS.md` (Agent Instructions)
4.  **First Commit:** `GitManager` initializes the repo and commits all files.
5.  **Remote Creation:** `GitHubApiClient` creates the repo on GitHub.
6.  **Push:** `GitManager` pushes the initial commit.

## 3. AI Coding Loop
1.  **Prompt:** User enters text in `IdeBottomSheet` or Contextual Overlay.
2.  **Context:** `AIDelegate` gathers:
    *   Current File / Selection.
    *   Build Logs (if error).
    *   Project Structure.
3.  **Request:** `JulesApiClient.createSession` sends the prompt.
4.  **Polling:** `AIDelegate` polls `listActivities`.
5.  **Patch:** If a `ChangeSet` artifact is received:
    *   `GitManager` applies the patch.
    *   `BuildService` triggers a verification build.
6.  **Feedback:**
    *   Success: Commit and Push.
    *   Failure: Send build log back to Jules.

## 4. Updates & Self-Healing
*   **Self-Update:** The IDE checks its own repository (`HereLiesAz/IDEaz`) for updates.
*   **Crash Reporting:** Fatal crashes are reported to the `IDEaz` repository as GitHub Issues with the `jules` label, triggering an auto-debugging workflow.
