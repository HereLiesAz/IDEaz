# Developer Workflow & Logic

This document outlines the operational workflows within IDEaz.

## 1. Build Strategy — Remote-Only

The "Race to Build" was retired in Phase 0. There is one build path now: **GitHub Actions**.

* **Web projects:** no build step. IDEaz renders the working tree directly. Only web projects are editable in IDEaz.
* **Publishing:** see §2.2 (Deploy). IDEaz does not build, download or install APKs.

The on-device toolchain (`aapt2`, `d8`, `kotlinc`, Maven Aether) was removed in Phase 0.

## 2. Project Lifecycle: Loading vs. Initialization

### 2.1 Loading (Preparation)
*   **Trigger:** User selects a project in the **Load** tab.
*   **Actions:** Clone / pull via JGit; check `ProjectAnalyzer.isPreviewable`; navigate to Setup tab.
*   **Note:** Loading does *not* start a build.

### 2.2 Initialization (Activation)
*   **Trigger:** User clicks **Open project** on the Setup tab.
*   **Actions:** Scaffold the directory from the bundled React starter if it has nothing previewable in it, then open the preview. Nothing is pushed.
*   **Publishing is separate and explicit.** The rail's **Deploy** item (`MainViewModel.deployWebProject`):
    1. `RepoDelegate.ensureRemoteRepository` links `origin`, creating a **private** repository on first publish (an existing public one is reused, with a warning).
    2. `forceUpdateInitFiles` writes or completes `.gitignore` (`utils/DeployIgnore`: `.env*`, `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`, `.ideaz/`, `node_modules/`, `dist/`, `build/`, `out/`), `AGENTS_SETUP.md` and `version.properties`; deletes an unmodified copy of the old `web_ci_pages.yml`; and untracks (keeping on disk) anything `.gitignore` now excludes that an earlier commit swept up. Staged by exact path, so unrelated uncommitted work is untouched. A new project's `.gitignore` is written before its initial commit.
    3. Commit and push, then `api/WebDeployClient` calls `POST https://workflows.hereliesaz.workers.dev/web-deploy` with the pushed SHA. The Worker (HereLiesAz/workflows, `worker/src/web-deploy.js`) checks the token can push to the repository and that HereLiesAz owns it, and dispatches its central `web-deploy.yml`: build the commit without deploy credentials, then publish **only the build output** (`dist/`, `build/` or `out/`; a no-build site minus dotfiles and secret-shaped files) to Cloudflare Pages.
    4. IDEaz polls the commit status `ideaz/web-deploy` on that SHA; its `target_url` is the live site (`https://ideaz-<name>-<id>.pages.dev`).
*   **Why not GitHub Pages:** Pages cannot serve a private repository on a free plan, and serving the repository itself is what used to publish `.env` files and `.ideaz/` history. The project carries no workflow at all.
*   **Limit:** the Worker builds only repositories owned by HereLiesAz (its credentials are the owner's). For anyone else Deploy pushes to their private repository and reports the refusal.
*   **What is sent where:** the push goes to GitHub with your token. The `POST` to the maintainer's Worker (`workflows.hereliesaz.workers.dev`) also carries your GitHub token, the repository name and the pushed SHA, and is made before the Worker decides whether it will serve the repository, so users whose repository is refused have still sent it. The token is used to check that you can push to the repository; the Worker is operated by the project maintainer.
*   **Gone:** `web_ci_pages.yml` (it published the repository root on every push), the Android `build.yml`/`release.yml` pair, `setup_env.sh` (a JDK + Android SDK bootstrap), and the two `antigravity-*.yml` files. Those last two installed an autonomous agent into the user's repository — `on: push`, `contents: write`, `pull-requests: write`, authenticated as the user — with no consent prompt anywhere on the path.

## 3. AI Coding Loop

| Provider | Adapter | Style |
|---|---|---|
| Gemini | `GeminiAdapter` (`ConversationalAiClient`) | Chat with tool-use (`read_file`, `write_file`, `list_files`, `apply_patch`); edits go through a checkpoint and the review gate (`AiEditApproval`): the AI writes behind a checkpoint, the review card lists what changed, and Approve keeps the edit or Reject restores the checkpoint |
| Claude | `AnthropicAdapter` (`ConversationalAiClient`) | Same tool-use contract as `GeminiAdapter` |
| OpenAI, DeepSeek, Groq, Cerebras, Hugging Face, Mistral | `OpenAiCompatibleAdapter` (`ConversationalAiClient`) | Same tool-use contract, one OpenAI-compatible client shared across all six |

Per-task model assignment (Default / Prompt Popup / Overlay Chat) is configured in Settings and routed by `MainViewModel.sendChatMessage`'s `taskKey` parameter.

## 4. The Error Handling Loop

### 4.1 User-Code Error
*   **Detection:** Build fails on Actions; failure not classified as IDE-internal.
*   **Action:** Build log routed back into the active AI chat session. Cycle repeats.

### 4.2 IDE Infrastructure Error
*   **Detection:** Stack trace from `com.hereliesaz.ideaz.*`, or an unhandled exception in IDEaz itself.
*   **Action:** `GithubIssueReporter` posts to `HereLiesAz/IDEaz` with label `jules`. Never sent to the user's AI.

## 5. CI/CD for IDEaz Itself

Every pull request runs `.github/workflows/pr-ci.yml` in this repository: `./gradlew :app:testDebugUnitTest :app:assembleDebug` on JDK 21, no secrets. It ends with the HereLiesAz/workflows `ci-report` action, which records the result on the central ci-status ledger and marks the file as target-local CI for the central sync (HereLiesAz/workflows offers no reusable CI workflow; each repository runs its own).

Releases are built centrally (`android-github-release.yml` / `android-play-release.yml` are trigger contracts for the HereLiesAz/workflows executors); see [`build_pipeline.md`](build_pipeline.md). Release and play variants require the release keystore — there is no debug-key fallback.

The dependency-submission workflow publishes the resolved release-runtime graph for `:app` and `:webruntime`. It does not submit test, lint, Gradle, AGP, or plugin configurations as application dependencies. Those tools run only in CI and are maintained through pinned workflow/plugin versions and Dependabot; the submitted graph is the production APK/AAB software bill of materials.

## 6. Repository Automation

Repository-neutral issue and pull-request automation runs through Antigravity CLI. The dispatcher accepts trusted `@antigravity-cli` comments and routes `/review`, `/triage`, or free-form requests to the reusable Antigravity workflows. Scheduled issue triage and contribution-guideline review use the same CLI and the `ANTIGRAVITY_API_KEY` repository secret.

Every generated project receives a four-component `version.properties`. Build commands, artifact globs, module paths, and default branches are detected or supplied through repository variables; no workflow assumes a repository name or branch name.

## 7. Updates & Self-Healing
*   **Self-update:** IDEaz checks `HereLiesAz/IDEaz` for updates.
*   **Live output:** Always show the bottom-card output indicator while a deploy or AI session is in flight.
