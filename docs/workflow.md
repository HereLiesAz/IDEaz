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
*   **Actions:** Clone / pull via JGit; check `ProjectAnalyzer.isPreviewable`; navigate to Setup tab.
*   **Note:** Loading does *not* start a build.

### 2.2 Initialization (Activation)
*   **Trigger:** User clicks **Save & Initialize** on the Setup tab.
*   **Actions:** Scaffold the directory from the bundled React starter if it has nothing previewable in it, then open the preview. Nothing is pushed.
*   **Publishing is separate and explicit.** The rail's **Deploy** item runs `forceUpdateInitFiles`, which writes `.github/workflows/web_ci_pages.yml`, `AGENTS_SETUP.md` and `version.properties` — and only those, staged by exact path so unrelated uncommitted work is untouched.
*   **Gone:** the Android `build.yml`/`release.yml` pair, `setup_env.sh` (a JDK + Android SDK bootstrap), and the two `antigravity-*.yml` files. Those last two installed an autonomous agent into the user's repository — `on: push`, `contents: write`, `pull-requests: write`, authenticated as the user — with no consent prompt anywhere on the path.

## 3. AI Coding Loop

| Phase | Provider | Adapter | Style |
|---|---|---|---|
| 1 (default) | Gemini | `GeminiAdapter` (`ConversationalAiClient`) | Chat with tool-use (`read_file`, `write_file`, `list_files`, `apply_patch`); writes directly to working tree; user commits manually |
| 1 (optional) | On-device model | `LocalLlmAdapter` (`ConversationalAiClient`) | Bounded JSON tool loop over the same four sandboxed project tools; malformed structured output degrades to text chat |
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

Release artifacts ship via tagged builds — every push updates a rolling "Latest Debug Build" prerelease, while the actual "Latest Release" only updates on a manually-triggered workflow run. See [`build_pipeline.md`](build_pipeline.md) §6.0 for the tag/trigger details.

The dependency-submission workflow publishes the resolved release-runtime graph for `:app` and `:webruntime`. It does not submit test, lint, Gradle, AGP, or plugin configurations as application dependencies. Those tools run only in CI and are maintained through pinned workflow/plugin versions and Dependabot; the submitted graph is the production APK/AAB software bill of materials.

## 6. Repository Automation

Repository-neutral issue and pull-request automation runs through Antigravity CLI. The dispatcher accepts trusted `@antigravity-cli` comments and routes `/review`, `/triage`, or free-form requests to the reusable Antigravity workflows. Scheduled issue triage and contribution-guideline review use the same CLI and the `ANTIGRAVITY_API_KEY` repository secret.

Every generated project receives a four-component `version.properties`. Build commands, artifact globs, module paths, and default branches are detected or supplied through repository variables; no workflow assumes a repository name or branch name.

## 7. Updates & Self-Healing
*   **Self-update:** IDEaz checks `HereLiesAz/IDEaz` for updates.
*   **Live output:** Always show the bottom-card output indicator while a remote build / AI session is in flight.
