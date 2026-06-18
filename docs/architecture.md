# IDEaz: Architecture

> **Authoritative source:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md). This file is the short overview.

## 1. Product Shape

IDEaz is an Android app that visually edits two kinds of GitHub-hosted projects:

| Target | Phase | Edit loop | Where it runs |
|---|---|---|---|
| **PWA** | 1 (daily driver) | sub-second WebView reload | `WebProjectHost` inside IDEaz |
| **Android app** | 2 (heavy artillery) | minutes (Jules + GitHub Actions) | sideloaded onto the device, observed via System Alert Window overlay |

Git is the source of truth. There is no offline build path — builds are always remote (GitHub Actions). On-device toolchain (`aapt2`, `d8`, `kotlinc`, Maven Aether) has been removed.

## 2. The Core Loop

1. **Pick or create a project.** Setup / Load / Clone tabs in `ProjectScreen`. `ProjectAnalyzer` detects the project type.
2. **Render the target.** PWA renders in `WebProjectHost`; Android target (Phase 2) renders by sideloading the APK and overlaying via `IdeazOverlayService`.
3. **Tap an element.** Bridge captures element context (selector + structure + screenshot region for PWA; `AccessibilityNodeInfo` chain for Android).
4. **Prompt the AI.** Phase 1: `ConversationalAiClient` (`GeminiAdapter`, BYO-key, tool-use loop). Phase 2: `AgenticAiClient` (`JulesAdapter`, PR-based).
5. **Apply changes.** Phase 1 writes directly to the working tree, then reloads the WebView. Phase 2 lets Jules open a PR; IDEaz auto-merges, polls Actions, sideloads the new APK.
6. **Commit.** PWA edits get a manual "Commit & Push" button; Android edits commit through Jules/GitHub.

## 3. Delegates

`MainViewModel` coordinates; logic lives in delegates under `ui/delegates/`:

* `AIDelegate` — AI sessions (Phase 1 Gemini, Phase 2 Jules; Phase 0 stubs the Jules call sites)
* `BuildDelegate` — remote build dispatch + polling + install
* `GitDelegate` — `GitManager` (JGit) wrapper
* `RepoDelegate` — GitHub API (clone, fork, secrets upload — see Phase 0 follow-ups)
* `OverlayDelegate` — overlay state + selection mode (Phase 2)
* `SystemEventDelegate` — package-install broadcasts
* `UpdateDelegate` — IDEaz self-update
* `StateDelegate` — shared mutable state

## 4. Services

* **`BuildService`** (`:build_process`): foreground service. Post-Phase-0 it is a thin shell around `RemoteBuildManager` — dispatches a remote build, polls GitHub Releases, downloads the artifact.
* **`IdeazOverlayService`**: `TYPE_APPLICATION_OVERLAY` window for Phase 2 element-tap on the sideloaded target app. Wired but inert until Phase 2.
* **`IdeazAccessibilityService`**: `AccessibilityNodeInfo` walk for Phase 2 element capture. Wired but inert until Phase 2.
* **`CrashReportingService`** (`:crash_reporter`): isolated process so crashes still report.
* **`ScreenshotService`**: `MediaProjection` virtual display for region screenshots — kept for Phase 2 (Android target) but **dormant in the PWA-only product**: undeclared in the manifest and gated off by `OverlayDelegate.screenCaptureEnabled`, so no consent prompt or foreground service runs today.

## 5. File System

* Projects: `context.filesDir/projects/{projectName}` (cloned via JGit).
* External projects can be registered; imports copy into internal storage.
* No more `local_build_tools/` — toolchain removed in Phase 0.

## 6. Networking

* **Retrofit** for GitHub and Jules.
* **Phase 1:** `ConversationalAiClient` adapter for Gemini (HTTP + streaming).
* **`AuthInterceptor`** injects keys; **`LoggingInterceptor`** sanitizes logs; **`RetryInterceptor`** handles backoff.

## 7. Out of Scope (Permanently)

React Native, Flutter, Python, generic web (non-PWA), Zipline / Redwood hot-reload, on-device build toolchain, VirtualDisplay-based `AndroidProjectHost`, "Race to Build" branching. These were deleted across Phase 0 (Tasks 2–8).
