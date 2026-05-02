# Developer Workflow & Logic

This document outlines the operational workflows within IDEaz.

## 1. Build Strategy — Remote-Only

The "Race to Build" was retired in Phase 0. There is one build path now: **GitHub Actions**.

* **PWA targets:** no build step. IDEaz renders the working tree directly.
* **Android targets:** push tag → Actions builds → IDEaz polls Releases → downloads APK → sideloads via `PackageInstaller`.

The on-device toolchain (`aapt2`, `d8`, `kotlinc`, Maven Aether) was removed in Phase 0.

## 2. Project Lifecycle: Loading vs. Initialization

### 2.1 Loading (Preparation)
*   **Trigger:** User selects a project in the **Load** tab.
*   **Actions:** Clone / pull via JGit; detect project type via `ProjectAnalyzer`; navigate to Setup tab.
*   **Note:** Loading does *not* start a build.

### 2.2 Initialization (Activation)
*   **Trigger:** User clicks **Save & Initialize** on the Setup tab.
*   **Actions:**
    1.  **Inject Workflows.** Force-push to `.github/workflows/`:
        *   `android_ci.yml` — debug build on push.
        *   `release.yml` — tagged release build, attaches signed APK to the GitHub Release.
        *   `codeql.yml` — security scanning (optional).
    2.  **Inject Environment.** Force-push `setup_env.sh` and `AGENTS_SETUP.md` to repo root.
    3.  **Start Build (Android only):** Tag and push; `RemoteBuildManager` polls.

## 3. AI Coding Loop

| Phase | Provider | Adapter | Style |
|---|---|---|---|
| 1 (default) | Gemini | `GeminiAdapter` (`ConversationalAiClient`) | Chat with tool-use (`read_file`, `write_file`, `list_files`, `apply_patch`); writes directly to working tree; user commits manually |
| 2 | Jules | `JulesAdapter` (`AgenticAiClient`) | PR-based; auto-merge (configurable); rebuild on merge |
| 3+ | Claude / OpenAI | new adapters | Same `ConversationalAiClient` interface |

## 4. The Error Handling Loop

### 4.1 User-Code Error
*   **Detection:** Build fails on Actions; failure not classified as IDE-internal.
*   **Action:** Build log routed back into the active AI session (Gemini chat in Phase 1; Jules session in Phase 2). Cycle repeats.

### 4.2 IDE Infrastructure Error
*   **Detection:** Stack trace from `com.hereliesaz.ideaz.*`, or `BuildService` exception.
*   **Action:** `GithubIssueReporter` posts to `HereLiesAz/IDEaz` with label `jules`. Never sent to the user's AI.

## 5. CI/CD for IDEaz Itself

The IDEaz project's own CI (`.github/workflows/`) builds the app on every push:
*   **Lint:** against the regenerated `app/lint-baseline.xml`.
*   **Unit tests:** `./gradlew :app:testDebugUnitTest`.
*   **Assemble debug:** `./gradlew :app:assembleDebug`.

Release artifacts ship via tagged builds.

## 6. Updates & Self-Healing
*   **Self-update:** IDEaz checks `HereLiesAz/IDEaz` for updates.
*   **Live output:** Always show the bottom-card output indicator while a remote build / AI session is in flight.
