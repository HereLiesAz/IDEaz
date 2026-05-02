# Task Flows

> **Authoritative source:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md), §3 (PWA loop) and §4 (Android loop).

## 1. Project Lifecycle

### 1.1 Loading (Preparation)
1.  **User:** Selects a project in the **Load** tab.
2.  **System:** Clones / pulls via `GitManager`; `ProjectAnalyzer` flags type (`pwa` / `android` — Phase 1 will add PWA detection); navigate to the **Setup** tab. Does *not* start a build.

### 1.2 Initialization (Activation)
1.  **User:** Clicks **Save & Initialize** in the Setup tab.
2.  **System:** Force-pushes `.github/workflows/android_ci.yml` and `release.yml`, plus `setup_env.sh` and `AGENTS_SETUP.md`. For Android targets only, this kicks off the first remote build. PWA targets need no build step.

## 2. The PWA Edit Loop (Phase 1)

1.  **Render:** `WebProjectHost` loads the project working tree under a `WebViewAssetLoader` virtual origin. `ideaz-bridge.js` is injected.
2.  **Tap:** User toggles select mode and taps an element. The bridge captures selector + outerHTML + computed styles + bounding rect + parent chain + screenshot region; sends the bundle to Kotlin via `addJavascriptInterface`.
3.  **Prompt:** `IdeBottomSheet` switches to the AI Chat tab; element preview shown as attachment. User types a prompt.
4.  **AI call:** `GeminiAdapter` (implementing `ConversationalAiClient`) sends conversation history + element context + (pruned) project file tree. Tools: `read_file`, `write_file`, `list_files`, `apply_patch`.
5.  **Apply:** Tool calls write to the working tree (no auto-commit). Streaming progress in the chat.
6.  **Reload:** WebView reloads when AI signals done.
7.  **Commit & Push (manual):** Button in the chat header → `GitManager.commit() + push()`.

## 3. The Android Edit Loop (Phase 2)

1.  **Build target:** Tap "Build" → tagged release build via `release.yml`. `RemoteBuildManager` polls Releases. APK arrives → `PackageInstaller` sideloads → auto-launch.
2.  **Inspect:** Target app full-screen on the device. IDEaz drops to a `TYPE_APPLICATION_OVERLAY`. User taps the floating IDEaz button → enters select mode.
3.  **Capture:** `IdeazAccessibilityService` walks the active window's `AccessibilityNodeInfo` tree, captures the tapped node (class, resource ID, text, content description, bounds, parent chain) and a region screenshot.
4.  **Prompt:** Floating overlay shows captured element + prompt input.
5.  **Dispatch to Jules:** `JulesAdapter` opens or resumes a session, posts the prompt + context.
6.  **Jules edits + opens PR:** Async. IDEaz polls activities; on PR-opened, auto-merges (default; configurable to wait-for-user-tap).
7.  **Re-build:** Merge triggers Actions. New APK arrives, sideloaded, target relaunches.

## 4. The Error Handling Loop

### 4.1 User-Code Error
*   **Detect:** Build / runtime error from the user's project.
*   **Route:** Phase 1 — back into the active Gemini chat as a system message ("the build failed: <log>"). Phase 2 — back into the active Jules session as a follow-up activity.
*   **Recovery:** AI fixes → working tree (Phase 1) or PR (Phase 2) → reload / rebuild.

### 4.2 IDE Infrastructure Error
*   **Detect:** Stack trace from `com.hereliesaz.ideaz.*`, missing tool, etc.
*   **Route:** `GithubIssueReporter` posts to `HereLiesAz/IDEaz` with the `jules` label.
*   **Constraint:** Never sent to the user's AI session.

## 5. Background Reliability
*   **Notification:** Persistent notification shows last 3 log lines.
*   **Polling:** Jules polling never times out — checks for *Activity* updates to show liveness.
