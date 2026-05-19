# PWA fast-path: skip GitHub for Web/PWA edits (design)

Date: 2026-05-18
Status: Spec — awaiting implementation plan
Scope: brainstorm items **C** (decouple Web/PWA build from GitHub) + **F** (auto-reload on AI write completion)

## Context

The user's IDE supports two project types: native Android (must build remotely because there is no on-device Android toolchain) and Web/PWA (just static files served by a WebView in-process). Today both paths route through `RemoteBuildManager` (Jules + GitHub Actions polling, 30-minute timeout, 5-second poll interval) for the *build* action, even though for a PWA the "build" is conceptually just "write files, refresh the preview." The remote round-trip serves no purpose for Web/PWA and breaks the immediate-update behavior the user wants.

Verified state of play:

- `BuildDelegate.kt:186-195` **already** skips `RemoteBuildManager` when `projectType` is WEB/PWA — it checks `index.html` exists and calls `onWebBuildSuccess(indexHtml.absolutePath)` directly. The "build" path is already split. **C is partially landed**.
- `MainViewModel.sendChatMessage():190` already fires `stateDelegate.triggerWebHardReload()` after the chat-tab Gemini turn completes. **F is partially landed** for the chat tab.
- `AIDelegate.runGeminiTask():252` fires `onFilesChanged()` (file-tree refresh) after the *contextual* AI task completes, but **not** a WebView reload. So the prompt-popup / contextual-chat path's edits don't refresh the live preview.
- `AIDelegate.runJulesTask()` (lines 293-338) for the contextual path: stateful Jules session, polls activities, extracts `gitPatch.unidiffPatch` patches. For PWA, Jules is the wrong tool — it expects a GitHub `sourceContext` and operates as a remote agent. There is no guard that prevents Jules from being routed for a PWA project.

So C+F is mostly closing gaps, not large new work.

## Goal

Web/PWA project lifecycle is fully local. AI edits land in the project directory and the WebView refreshes immediately, with zero GitHub or Jules round-trips. Android remains unchanged.

## Non-goals

- Removing GitHub entirely. The Deploy rail action (`viewModel.deployWebProject()`) stays — that's optional remote *hosting*, not the inner edit loop.
- Service-worker cache strategy for PWAs. Hard reload is good enough.
- Multi-tab / multi-preview management.
- File-system watching (inotify). Reloads are event-driven by AI completion, not by arbitrary disk writes.

## Design

### Part 1 — Guard Jules for Web/PWA in the contextual path

`AIDelegate.startContextualAITask()` at line 206 dispatches `AiModels.JULES_DEFAULT → runJulesTask(...)` regardless of project type. Add a check at the top of `runJulesTask` (or in the dispatch switch) that converts Jules-for-PWA into Gemini-for-PWA with a log entry:

```kotlin
val projectType = ProjectType.fromString(settingsViewModel.getProjectType())
if (projectType == ProjectType.WEB || projectType == ProjectType.PWA) {
    onLog("[AI] Jules is not used for Web/PWA projects. Routing through Gemini.\n")
    runGeminiTask(richPrompt, settingsViewModel.getGoogleApiKey())
    return
}
```

Rationale: the user's chosen `aiAssignment` may be `JULES_DEFAULT` because it's the global default; we don't want to silently fail or wait 45 seconds for a Jules poll that will never produce meaningful patches for a PWA. Route to Gemini, which uses the local tool-use loop and writes directly to disk.

### Part 2 — Reload preview after contextual AI completes (the F gap)

In `AIDelegate.runGeminiTask()` immediately after `adapter.chat()` returns and `onFilesChanged()` fires (line 252), add a project-type-gated reload trigger:

```kotlin
onFilesChanged()
if (isWebOrPwa()) {
    stateDelegate.triggerWebHardReload()
}
```

Plumbing: `AIDelegate` already takes `stateDelegate` (verify in constructor — if not, pass it via the existing callback set used for `onFilesChanged`). Add an `isWebOrPwa: () -> Boolean` callback rather than coupling `AIDelegate` directly to `SettingsViewModel`.

For `runJulesTask`: Part 1 routes PWA away from Jules entirely, so the Jules path remains Android-only. No reload needed there — Android handles its own install/launch via `onAndroidBuildSuccess`.

### Part 3 — Hot-reload precision

`MainViewModel.sendChatMessage():190` currently fires `triggerWebHardReload()` unconditionally. That's harmless for Android (the WebView isn't mounted) but it sets a `StateFlow<Long>` that nobody observes — a no-op. Leave it as is; the cost is one timestamp assignment per chat turn. No work needed here.

If we want to be tidy: gate it with the same `isWebOrPwa()` check used in Part 2. Tidy is optional — call it as a small polish step if convenient during implementation, otherwise skip.

### Part 4 — Soft vs hard reload policy

- Use **hard** reload (cache-bypass) after AI writes. Reason: HTML/CSS/JS that the AI just rewrote must not be served from the WebView cache.
- Soft reload (`triggerWebReload`) stays bound to the rail's "Reload" sub-item — the user's explicit request to re-fetch without busting cache.
- The existing rail items in `IdeNavRail.kt` already wire these correctly. No change.

## Files touched

- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/AIDelegate.kt` — Part 1 guard, Part 2 reload trigger.
- `app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt` — wire `isWebOrPwa` callback into `AIDelegate` constructor / setup.
- Possibly `app/src/main/kotlin/com/hereliesaz/ideaz/ui/delegates/BuildDelegate.kt` — no new code; just confirm no Jules invocation for PWA (verified already in lines 186-195).

No new classes. No new files.

## Verification

1. `./gradlew :app:assembleDebug` and `./gradlew build` — both `BUILD SUCCESSFUL`.
2. Set project type to PWA. Open a project with an `index.html`. Switch rail mode to "Interact" so the WebView is showing.
3. Open prompt popup → enter "change the page title to FOO" → submit.
4. **Expected:** within seconds (Gemini turn duration), the WebView reloads and shows `FOO` as the title. No GitHub commits made. No Jules session created.
5. Check console / system log tab: should show "[AI] Jules is not used for Web/PWA projects. Routing through Gemini." if the user's `aiAssignment` is Jules.
6. Switch to an Android project. Open prompt popup → enter "rename the start activity." Expect: Jules path runs as before (creates session, polls activities, applies patches via GitHub workflow). Behavior unchanged from today.

## Risks

- **AIDelegate constructor / wiring touchpoints**: adding callbacks may ripple through `MainApp` / DI. Risk is mechanical, not architectural. If the wiring is heavier than expected, do the simpler thing: pass `stateDelegate` and `settingsViewModel` directly into `AIDelegate` — they're already accessible via the same `MainViewModel` setup.
- **WebView reload timing**: if Gemini's turn writes multiple files across tool calls, `onFilesChanged()` fires once at turn end (good — single reload). Verify by triggering a multi-file edit ("split this component into three files") and confirming exactly one reload happens, not three.
- **Cache-bypass cost**: hard reload re-fetches all assets. For a local PWA all assets are file://, so cost is trivial. For a remote-loaded PWA (rare in this app's intended flow), cost is a full re-download. Acceptable.

## Open questions deferred

- Should the rail's **Reload** sub-item be hidden when project type is Android? Today it's only shown for WEB/PWA per `IdeNavRail.kt:68`. Already correct.
- Should we expose "auto-reload after AI" as a user setting (in case someone wants to inspect before reloading)? Default ON; revisit if requested.
