# IDEaz: Architecture

> **Authoritative source:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md). This file is the short overview.

## 1. Product Shape

IDEaz is an Android app that visually edits two kinds of GitHub-hosted projects:

| Target | Phase | Edit loop | Where it runs |
|---|---|---|---|
| **PWA** | 1 (daily driver) | sub-second WebView reload | `WebProjectHost` inside IDEaz |
| **Android app** | 2 (heavy artillery) | sub-second local preview (on-device Wasm compile) for editing; minutes (Jules + GitHub Actions) for the real APK | local preview renders in `WebProjectHost` like a PWA; the sideloaded APK is still observed via System Alert Window overlay, wired but inert until Phase 2 |

Git is the source of truth. The shipped APK is never built on-device — that build is always remote (GitHub Actions), and the old on-device APK toolchain (`aapt2`, `d8`, `kotlinc`-for-JVM, Maven Aether) stays removed. A narrower on-device compiler was reintroduced since, but only for a **local preview**: `WasmCompilerService` compiles Android (Compose Multiplatform) projects' `commonMain`/`wasmJsMain` sources to Wasm and mounts the result in `WebProjectHost`, giving Android projects the same fast local loop PWAs already had. See §5.

## 2. The Core Loop

1. **Pick or create a project.** Setup / Load / Clone tabs in `ProjectScreen`. `ProjectAnalyzer` detects the project type.
2. **Render the target.** PWA renders in `WebProjectHost`. Android now renders there too, as a Compose Multiplatform / Wasm local preview compiled on-device by `WasmCompilerService`; the sideloaded-APK overlay path (Phase 2) via `IdeazOverlayService` remains wired but inert.
3. **Tap an element.** Bridge captures element context (selector + structure + screenshot region for PWA; `AccessibilityNodeInfo` chain for Android).
4. **Prompt the AI.** Phase 1: `ConversationalAiClient`, implemented by `GeminiAdapter`, `AnthropicAdapter`, `OpenAiCompatibleAdapter` (Groq/Cerebras/HF/Mistral/OpenAI/DeepSeek), the on-device `LocalLlmAdapter`, and the `GeminiAppBridgeAdapter` — all BYO-key except the local one. Every one of these now shares the same edit-checkpoint/review/approve/reject/undo contract (`IdeTools` checkpoints + `LocalEditApproval`/`LocalEditApprovalRequiredException`, package `ai.local` despite being provider-agnostic), not just the local adapter. Phase 2: `AgenticAiClient` (`JulesAdapter`, PR-based).
5. **Apply changes.** Phase 1 writes directly to the working tree behind a checkpoint the user approves or rejects, then reloads the WebView. Phase 2 lets Jules open a PR; IDEaz auto-merges, polls Actions, sideloads the new APK.
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
* **`ScreenshotService`**: `MediaProjection` virtual display for region screenshots. Declared in the manifest (`mediaProjection` FGS) and started **only for Android target projects**, gated at runtime by `OverlayDelegate.isScreenCaptureEnabled()`; web/PWA projects never raise the consent prompt or start the service.

## 5. File System

* Projects: `context.filesDir/projects/{projectName}` (cloned via JGit).
* External projects can be registered; imports copy into internal storage.
* No more `local_build_tools/` — the on-device APK toolchain removed in Phase 0.
* `context.filesDir/www`: output of the on-device Wasm/Compose-Multiplatform local preview compile (`WasmCompilerService`), mounted by `WebProjectHost`. Distinct from the removed APK toolchain — this never produces anything that ships; the real APK still comes only from GitHub Actions.

## 6. Networking

* **Retrofit** for GitHub and Jules.
* **Phase 1:** `ConversationalAiClient` adapters for Gemini, Anthropic, and OpenAI-compatible providers (HTTP + streaming), plus the on-device local adapter and the Gemini-app bridge.
* **`AuthInterceptor`** injects keys; **`LoggingInterceptor`** sanitizes logs; **`RetryInterceptor`** handles backoff.

## 7. Out of Scope (Permanently)

React Native, Flutter, Python, generic web (non-PWA), Zipline / Redwood hot-reload, the on-device **APK-build** toolchain (`aapt2`/`d8`/`kotlinc`-for-JVM/Maven Aether), VirtualDisplay-based `AndroidProjectHost`, "Race to Build" branching. These were deleted across Phase 0 (Tasks 2–8). (A narrower on-device *Wasm preview* compiler was reintroduced later for Android/Compose-Multiplatform local editing — see §5 — but it is not an APK toolchain and doesn't produce a shippable artifact.)
