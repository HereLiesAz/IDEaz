# Platform Decision Helper: PWA or Android-Native

IDEaz supports exactly two project shapes:

| | **PWA** (Phase 1) | **Android app** (Phase 2) |
|---|---|---|
| Edit loop | sub-second WebView reload | minutes (Jules + GitHub Actions) |
| AI provider | Gemini (BYO-key, conversational) | Jules (PR-based, agentic, async) |
| Renders in | `WebProjectHost` (WebView) inside IDEaz | sideloaded onto the device, observed via `IdeazOverlayService` |
| Build | none — IDEaz reads the working tree | remote-only, GitHub Actions |
| When to pick this | UI-heavy work, marketing pages, prototypes, anything that fits a PWA | needs native APIs, sensors, native UI components |

Both shapes are GitHub-tethered: every project is a Git repository, and Git is the source of truth.

## When to start with PWA (Phase 1 default)

* The work is primarily visual / DOM-shaped.
* You want the fastest possible iteration loop.
* The end target is a web app *or* you are prototyping before committing to a native build.

## When to go straight to Android (Phase 2)

* You need device APIs the WebView cannot reach (sensors, BLE, hard background work).
* The product *must* ship as a `.apk` to a store or sideload channel.
* You can tolerate the multi-minute remote-build cycle as the cost of native fidelity.

## Out of scope (not options)

React Native, Flutter, Python, generic "web" projects (only PWAs). These were removed in Phase 0. See `plans/2026-05-01-ideaz-revival-design.md` §"What IDEaz is *not* (anymore)".
