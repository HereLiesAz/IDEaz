# IDEaz Revival — Design

**Date:** 2026-05-01
**Status:** Approved (brainstorming → design)
**Next step:** Break Phase 0 into a concrete implementation plan via `superpowers:writing-plans`.

---

## 1. Product Definition

> IDEaz is an Android app on your phone that lets you visually edit two kinds of projects: **Progressive Web Apps** (rendered in a WebView, edited live, sub-second loop) and **Android apps** (overlaid on the running app, edited via Jules + GitHub Actions, multi-minute loop). Git is the source of truth. Every project is a GitHub repo.

### What IDEaz is
- Android-only as a host (the IDE itself runs on the phone).
- Targets: PWAs (Phase 1, daily driver) and Android apps (Phase 2, heavy artillery).
- GitHub-tethered. Every project is a GitHub repo. No offline builds.
- Single-purpose. Post-code editing only. Code editor and file explorer remain as escape hatches, not the workspace.

### What IDEaz is *not* (anymore)
- Not multi-platform: no React Native, no Flutter, no Python, no general "Web" path beyond PWA.
- Not an offline IDE: no on-device `kotlinc` / `aapt2` / `d8`. No Maven/Aether resolver.
- Not a hot-reload tool: no Zipline, no Redwood codegen, no JS guest layer.
- Not a hybrid host: no VirtualDisplay launching arbitrary apps.

### AI providers
| Phase / distribution | Provider | Notes |
|---|---|---|
| 1 — Google Play | **Gemini API** | BYO AI Studio key in Settings. The Play build exposes no external-app accessibility or overlay surface. |
| 1 — GitHub APK | **Installed Gemini app, Gemini API fallback** | When the user explicitly enables the package-scoped IDEaz accessibility service, the GitHub build may hand the redacted project and conversation to a Gemini-capable installed app. An AI Studio key is optional and becomes the fallback. |
| 2 | **Jules** | For Android targets. PR-based, agentic, async. |
| 3 (post-MVP) | **Claude, OpenAI-compatible providers** | BYO-key, slot into the same `ConversationalAiClient` contract. |

The Phase-1 installed-app bridge is **not** the Phase-2 Android-target accessibility
loop. Its service is registered only in GitHub builds, restricted to supported Gemini
packages in the accessibility config, and active only while an IDEaz AI handoff is in
flight. It does not inspect arbitrary target apps or replace the in-WebView PWA element
bridge.

### Out-of-scope, permanently
- "Race to Build" (no local pipeline to race against).
- WebView-hosted general web projects (only PWAs).
- Manifest signing via Ed25519 / LazySodium (Zipline).
- On-device toolchain extraction for `aapt2` / `d8` / `kotlinc` / `java`.

---

## 2. Components — Stay / Go / New

### Stay (and harden)

| Component | Role |
|---|---|
| `MainActivity` + `MainViewModel` + 6 delegates | App shell, no major rework needed |
| Settings screen + encrypted credentials | Already solid |
| Project Screen (Setup / Load / Clone tabs) | Tabs remain; project-type detection broadens |
| `GitManager` (JGit) | Used for both PWA and Android repos |
| `WebProjectHost` | **Promoted to primary host.** Needs DOM bridge, element-tap protocol, screenshot capture, reload control |
| `FileExplorerScreen` + code editor | Escape hatches |
| `IdeBottomSheet` + `LogcatReader` | Console / log streaming |
| `ProjectAnalyzer` | Needs PWA detection added |
| Native-target overlay/accessibility inspection | Phase 2 only. Distinct from the package-scoped Phase-1 external-AI bridge. |
| `JulesApiClient` | Phase 2. Compile errors get **stubbed** in Phase 0, fixed in Phase 2 |

### Go (deleted in Phase 0)

| Component | Why |
|---|---|
| `ReactNativeActivity`, `ReactNativeBuildStep`, RN templates, all RN `.so` libs | Out of scope. Frees up tens of MB of APK + 60+ native libs that block stripping |
| Python: `PythonInjector`, `todo-python.md`, `python_implementation.md` | Out of scope |
| Flutter: `flutter_investigation.md`, `todo_flutter.md`, any flutter code paths | Out of scope (was remote-only anyway) |
| On-device toolchain: `KotlincCompile`, `D8Compile`, `Aapt2Compile`, `Aapt2Link`, `ApkSign`, `HttpDependencyResolver`, `ToolManager`, all aapt2/d8/kotlinc asset extraction | Remote-only build |
| `VirtualDisplay` host code | Lost the architecture vote |
| Redwood codegen + Zipline (`ZiplineManifestGenerator`, K2JS integration, manifest signing TODO) | Out of scope |
| `JulesCliClient` | Already deprecated; finally remove it |
| "Race to Build" branching in `BuildDelegate` | One pipeline, not two |
| Most of `BuildService` | What remains is just polling Actions for release artifacts |
| `lint-baseline.xml` RN entries | Will be regenerated cleanly |
| Stale docs: `react_native_implementation_plan.md`, `flutter_investigation.md`, `python_implementation.md`, `todo_python.md`, `todo_flutter.md`, `todo_react_native.md`, `Dynamic Android App Development Exploration.md`, `Modular Android Development Approaches.md`, `Hybrid Host Architecture Implementation Checklist.md`, `audit_report.md`, `contradictions_report.md` | Stale or about deleted features |

### New (Phase 1 build)

| Component | Role |
|---|---|
| **DOM bridge** (`ideaz-bridge.js` + Kotlin postMessage handler) | Element-tap → selector + HTML + computed styles + screenshot region back to IDE |
| **`ConversationalAiClient` interface** + provider adapters | Common conversational/tool-use contract across Gemini and other providers |
| **GitHub external-AI app host** | Optional installed-Gemini handoff, serialized accessibility driver, redacted repo snapshot, explicit attachment sharing, selectable presentation mode, API fallback |
| **AI tool definitions** (`read_file`, `write_file`, `list_files`, `apply_patch`) | Structured editing, not whole-file rewrites |
| **`PwaProjectDetector`** (extension of `ProjectAnalyzer`) | Detects `manifest.webmanifest` / service worker / standard PWA shape |
| **PWA project template** | One-click `Create PWA` produces a minimal install-ready PWA scaffold in a fresh repo |
| **`AiChatTab`** | New tab in `IdeBottomSheet` — conversational, distinct from existing Jules session UI |

### New (Phase 2 build)

| Component | Role |
|---|---|
| **`AgenticAiClient` interface** + `JulesAdapter` | One method: `dispatchTask(prompt, context): Flow<TaskEvent>`. Polls under the hood |
| **Native-Android element bridge** | Mirrors the PWA bridge's *shape* so the chat UI is target-agnostic |

---

## 3. The PWA Target Loop (Phase 1)

### End-to-end loop

1. **Project setup.** User picks one of: *Create PWA* (template scaffold pushed to a fresh GitHub repo), *Load PWA* (open an existing local clone), *Clone* (clone a GitHub URL). `ProjectAnalyzer` flags type → `pwa`.
2. **Render.** IDEaz renders the project working tree under a virtual `https://appassets.androidpublisher.com/` origin via `WebViewAssetLoader` — gives proper origins + service-worker support, no real network.
3. **Inject.** On every page load, IDEaz injects `ideaz-bridge.js` into the WebView, exposing `window.ideaz`: `selectMode(on|off)`, `onElementTap(callback)`, `getElementContext(el)`.
4. **Tap.** User toggles select mode (overlay button), taps a DOM element. The bridge captures CSS selector, outerHTML, computed style highlights, bounding rect, parent chain (3 levels), screenshot of the region. Sends to Kotlin via `addJavascriptInterface` → `WebViewBridge.kt`.
5. **Prompt.** `IdeBottomSheet` flips to *AI Chat* tab with the captured element preview shown as an attachment. User types: e.g. "make this button bigger and use the brand purple."
6. **AI call.** `AiAdapterFactory` selects the configured provider. The Play build uses documented provider APIs. The GitHub APK may use the installed Gemini app when the user has explicitly enabled the package-scoped service; it sends a redacted bounded repo snapshot, full IDEaz conversation and explicit current-turn attachments, then captures the completed answer. If an AI Studio key exists, the Gemini API remains the automatic fallback. Cloud adapters retain structured IDE tools.
7. **AI response/edit.** Tool-capable APIs execute through `IdeTools`. The installed-app bridge accepts only a complete unified diff and routes it through the same checkpoint, validation and explicit approval contract before the preview can reload.
8. **Reload.** Once the user approves a real edit, WebView reloads. New rendering visible.
9. **Optional commit.** "Commit & Push" button in chat header triggers `GitManager.commit(message: aiSuggestedCommitMessage)` + `push()`.

### Data flow (one round trip)

```
User taps element
  → ideaz-bridge.js
  → window.ideaz.send(JSON.stringify(elementContext))
  → @JavascriptInterface in WebViewBridge.kt
  → ViewModel state update
  → AiChatTab shows attachment + input
User types prompt + sends
  → ViewModel.sendMessage()
  → AiAdapterFactory
      → Play/API path: provider adapter + IdeTools
      → GitHub installed-app path: redacted project.txt + conversation + attachments
  → response / tool calls / unified diff
  → IdeTools checkpoint + review contract for every mutation
User approves edit
  → WebProjectHost.reload()
  → WebView shows new rendering
User taps Commit & Push
  → GitManager.commit() + push()
```

### Failure handling

| Failure | Handling |
|---|---|
| Installed Gemini unavailable, times out, or cannot accept the share | If an AI Studio key exists, fall back to Gemini API; otherwise surface a clear bridge error in chat |
| Gemini/API error (rate limit, key invalid) | Show in chat as system message; key-invalid → deep-link to Settings |
| AI hallucinates a path that doesn't exist | Tool returns error; AI sees error and retries (or gives up after N attempts) |
| AI writes broken HTML/JS that won't render | WebView console logs streamed to *Console* tab; user sees errors, can prompt "fix this error" |
| User edits a file directly via File Explorer mid-AI-turn | Checkpoint fingerprints prevent automatic restore from clobbering later user edits |
| Service worker caches an old version | "Hard reload" button bypasses cache; defer cache-busting niceties to later |
| Network down | Installed-app/API AI still depends on the chosen provider's connectivity. File Explorer and local preview remain available |

### Authentication and distribution boundary
- **Google Play:** Gemini and every other hosted provider is BYO-key. No external-AI accessibility service or overlay permission is declared.
- **GitHub APK:** the installed Gemini path needs no API key, but requires the user to explicitly enable IDEaz's restricted, package-scoped accessibility service. AI Studio key remains optional fallback.
- **GitHub OAuth (clone/commit/push):** existing flow stays.

---

## 4. The Android Target Loop (Phase 2)

The original IDEaz vision, deferred until Phase 1 is working. Most of the code already exists; the work is *fixing* and *narrowing*, not building from scratch.

### End-to-end loop

1. **Project setup.** Same screen as PWA. `ProjectAnalyzer` flags type → `android`. Project must already build via `./gradlew assembleDebug` on its existing GitHub Actions workflow (or IDEaz injects a default one).
2. **Workflow injection.** On first run, IDEaz force-pushes a standardized `android_ci.yml` workflow (and `release.yml` for tagged builds) to the repo. Reuses existing `WorkflowDelegate` code, simplified.
3. **Build & install.** "Build" tap triggers a tagged release build. IDEaz polls Releases API; when APK appears, downloads, sideloads via `PackageInstaller`. Auto-launches.
4. **Inspect.** Target app is now running on the device, full-screen. IDEaz drops to a `TYPE_APPLICATION_OVERLAY` window. User taps the floating IDEaz button → enters select mode → taps a UI element on the target app.
5. **Capture.** The Phase-2 native-target accessibility service walks the active target window's `AccessibilityNodeInfo` tree, identifies the node under the tap, captures: class name, resource ID, text, content description, bounds, parent chain. Plus a screenshot of the bounded region.
6. **Prompt.** Floating overlay shows captured element preview + prompt input.
7. **Dispatch to Jules.** `JulesApiClient` opens or resumes a session for this project, posts the prompt + context as a new activity. Polls for completion.
8. **Jules edits + opens PR.** Jules works asynchronously on the repo. IDEaz polls activities; on PR-opened, **auto-merges** (default; configurable to wait-for-user-tap).
9. **Re-build.** Merge triggers Actions; IDEaz polls Releases as in step 3. New APK lands, gets installed, target relaunches.
10. **See change.** User sees the edit in the running target app. Repeat.

### Failure handling (Phase 2 specifics)

| Failure | Handling |
|---|---|
| Jules session can't resume | Auto-create a new session, log to chat |
| Jules opens a PR but Actions build fails | Build log piped to *Console* tab; user can prompt Jules "the build failed: <log>" to retry |
| `PackageInstaller` install fails (signature mismatch with previously-installed app) | Prompt user to uninstall the old version; offer one-tap uninstall |
| AccessibilityService node tree is stale | Tap silently retries with fresh tree; if still stale, show "tap again" toast |
| User abandons a Jules task mid-flight | Cancel button cancels session; uncommitted PR left for manual review on GitHub |

---

## 5. Phasing & Milestones

Each phase ends with a usable artifact — even Phase 0. Stop early if scope drifts.

### Phase 0 — Triage (the compile gate)

**Goal:** Codebase compiles cleanly with all dead weight removed.

**Work:**
- Delete RN, Flutter, Python paths.
- Delete on-device toolchain (`KotlincCompile`, `D8Compile`, `Aapt2Compile`, `Aapt2Link`, `ApkSign`, `HttpDependencyResolver`, `ToolManager`).
- Delete Zipline + Redwood.
- Delete `VirtualDisplay` host + "Race to Build".
- Delete `JulesCliClient`. Stub `JulesApiClient` calls in `AIDelegate` and `ProjectScreen` so they compile.
- Modernize Compose `TabRow` + `LocalLifecycleOwner` deprecations.
- Regenerate `lint-baseline.xml` from a clean state.
- Delete stale docs.

**Milestone:** `./gradlew :app:assembleDebug` succeeds with **zero compile errors and zero deprecation warnings from deleted code paths**, on a fresh Gradle daemon, in CI.

**Estimated size:** ~1–2 days of focused work.

### Phase 1 — PWA Target Loop

**Goal:** End-to-end working PWA editor.

| Sub-phase | Work | Milestone |
|---|---|---|
| **1A — Render** | `WebProjectHost` + `WebViewAssetLoader`, virtual origin, reload controls | Open hand-written PWA from local Git repo, see it render, manual reload after file edit |
| **1B — Bridge** | `ideaz-bridge.js`, element-tap protocol, `WebViewBridge.kt`, Select Mode toggle | Tap element in PWA; captured context appears as structured object in IDE log |
| **1C — AI** | `ConversationalAiClient`, provider adapters, tool defs, `AiChatTab`, Settings credentials, GitHub external-AI host | Play: BYO-key provider works. GitHub: installed Gemini works keylessly when explicitly enabled and falls back to API when configured. Both preserve full IDEaz conversation context. |
| **1D — The Loop** | Wire 1B → 1C, approval-gated reload, Commit & Push, Create PWA template | Tap *Create PWA*, get fresh PWA running, tap a button, type "make this red," review/approve, commit. ~5s end-to-end on simple changes |

**Phase 1 done when:** 1D milestone holds for three different toy PWAs without error.
**Estimated size:** ~6–8 days total.

### Phase 2 — Android Target Loop

**Goal:** Original IDEaz vision works.

| Sub-phase | Work | Milestone |
|---|---|---|
| **2A — Jules Plumbing** | Fix `JulesApiClient` signature drift, restore `AIDelegate`, `JulesApiClientTest`, simplify workflow injection | Send prompt to Jules from IDEaz; observe PR appear on GitHub |
| **2B — Overlay** | Native-target accessibility/overlay service and element bridge mirroring PWA bridge shape | Tap element in sideloaded target app; captured context appears in chat |
| **2C — Loop** | Auto-merge PRs, build polling, release artifact download, `PackageInstaller`, auto-launch, build-log streaming | Tap-prompt-edit-build-install-relaunch round trip works for a toy Android app |

**Phase 2 done when:** 2C milestone holds end-to-end for two different Android toy projects.
**Estimated size:** ~4–5 days total.

### Phase 3 — More AIs (post-MVP)

- Continue adding/swapping adapters behind `ConversationalAiClient` as useful.
- **Stop here unless** Phase 2 is solid and you have appetite.

### Phase 4 — Polish (deferred indefinitely)

- File watcher → auto-reload.
- Project history / undo (Git-based — already free).
- Server-mode for `npm run dev` PWAs.
- Element-tap heat map / multi-select.

---

## 6. Risks, Open Questions, Testing

### Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Consumer Gemini UI/accessibility surface changes | High | Medium | Package-scoped service, tolerant semantic matching, completion checks, API fallback |
| Gemini API surface drift | High | Medium | Thin adapter; pinned wire model; provider abstraction bounds blast radius |
| WebView service-worker support varies by version | Medium | Medium | `WebViewAssetLoader` papers over most; min WebView version check at startup |
| Jules API drifts again | Medium | Low (Phase 2 only) | Integration test hitting Jules sandbox; pin to current snapshot; keep stub fallback |
| GitHub Actions free-tier minutes exhaustion | Low solo / High wider | Medium | Document limit; show remaining minutes in Settings |
| `PackageInstaller` UX changed across Android 12/13/14 | Medium | Medium (Phase 2) | Hand-test on 12, 14, 15 emulators before release |
| Accessibility permission anxiety | High | High | Play build has no external-AI service. GitHub bridge is explicit, package-scoped and idle outside a request; Phase-2 target inspection gets a separate explainer |
| Single-developer abandonment recurrence | Medium | Existential | Phasing built so each phase ships something usable; no phase hostage to next |

### Open questions (deferred decisions)

1. **Screenshot to AI?** Send element-region screenshot as model image-input, or text-only? *Default: explicit reference attachments only for Phase 1.*
2. **Project tree size threshold?** *Default: bounded redacted snapshot for the installed-app path; file tools for capable APIs.*
3. **Hot reload sophistication?** *Default: hard reload always for Phase 1.*
4. **Keep crash-auto-reporter?** *Default: keep, simplify.*
5. **Create PWA template — single or selectable?** *Default: single opinionated vanilla template.*
6. **GitHub OAuth scope.** *Default: keep current; revisit before public release.*

### Testing strategy

**Where TDD pays off:**
- `ConversationalAiClient` adapters (mock HTTP).
- `AiTools` executors (mock filesystem).
- Repo snapshot containment/redaction.
- External-AI transcript and completion heuristics.
- `PwaProjectDetector` (fixture projects under `src/test/resources`).
- `WebViewBridge` message marshaling.
- `JulesApiClient` (Phase 2).

**Where TDD doesn't pay off (hand-test only):**
- WebView rendering correctness.
- External consumer-app UI automation across app versions.
- Native-target overlay / accessibility tap capture.
- The end-to-end loop.

**The floor (every PR):**
- GitHub distribution compile (`assembleDebug`) green.
- Play distribution bundle (`bundlePlay`) green.
- `testDebugUnitTest` green.
- Lint passes against the regenerated baseline.

**Per-milestone smoke tests:** one short hand-test checklist per sub-phase, ≤5 min, in `docs/plans/<phase>-smoke-test.md`.

**No coverage targets.** Coverage on a solo project burns time without preventing real bugs.

**Skills involved:**
- `superpowers:test-driven-development` for unit-level pieces.
- `superpowers:verification-before-completion` before every PR claim.
- `superpowers:requesting-code-review` before merging anything past Phase 0.
