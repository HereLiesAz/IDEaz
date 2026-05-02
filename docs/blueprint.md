# Architectural Blueprint for IDEaz

> **Authoritative source:** [`plans/2026-05-01-ideaz-revival-design.md`](plans/2026-05-01-ideaz-revival-design.md).

## 1. Vision: The Visual, Post-Code IDE

IDEaz is an Android app that visually edits two kinds of projects:

* **Progressive Web Apps (Phase 1, daily driver):** rendered live inside `WebProjectHost`, sub-second edit loop driven by Gemini.
* **Android apps (Phase 2, heavy artillery):** sideloaded to the device, overlaid via System Alert Window, edited via Jules opening PRs that auto-build via GitHub Actions.

Git is the source of truth. Every project is a GitHub repo. There is no offline build.

### Core loop

```
Pick / Create project
  → Render target (WebView for PWA; sideloaded APK + overlay for Android)
  → Tap element (DOM bridge or AccessibilityNodeInfo)
  → Prompt AI with element context
  → AI applies changes (Gemini tools for PWA; Jules PR for Android)
  → Reload (WebView reload for PWA; build → install for Android)
  → Commit & push
```

## 2. What IDEaz is *not*

* Not multi-platform: no React Native, no Flutter, no Python, no generic-web path.
* Not an offline IDE: no on-device `kotlinc` / `aapt2` / `d8`, no Maven resolver.
* Not a hot-reload tool: no Zipline, no Redwood codegen.
* Not a hybrid host: no `VirtualDisplay`-based `AndroidProjectHost`.

## 3. AI Providers

| Phase | Provider | Notes |
|---|---|---|
| 1 (default) | **Gemini** | Free tier on-ramp; BYO-key; PBKDF2-encrypted credentials |
| 2 | **Jules** | Android target loop; PR-based, agentic, async |
| 3 (post-MVP) | **Claude / OpenAI** | Slot into the same `ConversationalAiClient` interface |

## 4. Build Pipeline

Remote-only via GitHub Actions. On "Save & Initialize", IDEaz force-pushes a standardized `android_ci.yml` + `release.yml` to the project repo. Builds dispatch via push or workflow_dispatch; IDEaz polls GitHub Releases for the artifact and sideloads via `PackageInstaller`. PWAs do not need a build step — IDEaz renders the working tree directly.

## 5. Error Handling

* **User-code error:** build log routed back to the AI session (Phase 1 Gemini chat; Phase 2 Jules session).
* **IDE infrastructure error:** stack trace posted to `HereLiesAz/IDEaz` as a GitHub Issue with the `jules` label. Never sent to the user's AI.

## 6. Initialization vs Loading

### Loading
User selects a project on the Load tab → JGit clone/pull → `ProjectAnalyzer` flags type → navigate to Setup tab.

### Initialization
User taps "Save & Initialize" on the Setup tab → IDEaz force-pushes `.github/workflows/android_ci.yml`, `release.yml`, plus `setup_env.sh` / `AGENTS_SETUP.md` → first build is queued.

## 7. Developer Tools (Escape Hatches)

`FileExplorerScreen` and the basic code editor remain for emergencies — verifying AI-generated code, manual recovery — but they are **not** the workspace. The visual loop is.

## 8. Phasing

* **Phase 0 — Triage** (this branch): delete dead code paths, regain a green build.
* **Phase 1 — PWA loop**: `WebViewAssetLoader` host + DOM bridge + `GeminiAdapter` + chat tab.
* **Phase 2 — Android loop**: fix Jules call sites, restore overlay/accessibility, auto-merge → build → sideload.
* **Phase 3+ — More AIs and polish.**

See the design doc and phase plans for milestone detail.
