# IDEaz: Architecture

## 1. What this is

IDEaz is a **Kotlin/Compose Multiplatform** app that visually edits web projects.
You render your project, tap an element, describe the change, and an AI edits the
source. Git is the source of truth.

Two targets, one source tree:

| Target | What it is |
|---|---|
| **android** | The phone IDE. The product's original identity. |
| **desktop** (JVM) | The same app on a laptop. Not a second product — it is how the app becomes runnable and testable without a handset. |

Android itself has two distributions from that source tree:

| Distribution | Gradle build type | External installed-AI host |
|---|---|---|
| **GitHub APK** | `debug` / `release` | Yes, explicitly enabled by the user |
| **Google Play** | `play` | No; provider APIs only |

The desktop target exists for a specific reason. For most of this project's life
nothing here had ever executed on a device: no instrumented tests, no emulator in
CI, no recorded session. The bill came due once already, when a release shipped
with **every credential save silently failing** because a caller-generated IV was
passed to an AndroidKeyStore key — found by reading code, not by launching the
app. `./gradlew :app:run` makes "launch it and click the loop" something a person
or a CI job can do in seconds.

Both targets are JVM, which is what makes sharing tractable: JGit, OkHttp and
Retrofit run unchanged on both.

## 2. The core loop

```
Open project → preview it → tap an element → describe the change
             → AI edits the source → review the diff → approve → reload
```

Everything else in this repository is in service of that sentence, or should not
be here.

**The element→source problem** is solved by the preview pipeline itself:
`ideaz-loader.js` transpiles the project's own source in-browser with Babel and
does so with `development: true`, enabling JSX source metadata. React exposes
that metadata on the fiber as `_debugSource`; `ideaz-bridge.js` walks the fiber
tree from the tapped node and reads it. The AI therefore receives
`src/App.jsx:42` rather than a CSS selector and a search problem.

Projects with no such metadata still work — the bridge falls back to a
`data-ideaz-source` attribute, then to selector + surrounding HTML, and the
model's system preamble tells it which it is getting.

## 3. Source layout

```
commonMain            platform-agnostic (Compose UI, pure Kotlin)
  └── jvmSharedMain   + the JVM stdlib, JGit, OkHttp, Retrofit
        ├── androidMain
        └── desktopMain
```

`commonMain` compiles to platform-agnostic metadata and cannot touch `java.*`,
which rules out almost everything real here. `jvmSharedMain` is the intermediate
source set both JVM targets share, and is where the bulk of the logic lives: the
AI layer (`IdeTools` and its checkpoint machinery, provider adapters, tool
schema, edit-approval contract), the GitHub API client, `GitManager`,
`StateDelegate`, `ProjectAnalyzer`, `RepoSnapshot`, `GithubSecretBox`.

The platform seam is deliberately tiny — one `expect object Platform` covering
logging, Base64, and a debug-build flag.

## 4. Rendering

`WebProjectHost` mounts the project's working tree at the origin root of
`https://appassets.androidplatform.net/` via `WebViewAssetLoader`, so it gets a
real origin and service-worker support with no network.

There is no bundler on a phone, so `webruntime/` ships a vendored JS runtime
(React, React-DOM, Babel, and common ecosystem libraries) plus
`ideaz-loader.js`, which transpiles JSX/TS on demand and rewrites relative
imports to `blob:` URLs.

`WebProjectPathHandler` marks project content `no-store` while the bundled runtime
is cacheable and version-segmented by `BuildConfig.VERSION_CODE`, so an app
upgrade cannot serve stale runtime JS.

## 5. AI providers

Eight registered hosted providers sit behind three API adapter families:
`GeminiAdapter`, `AnthropicAdapter`, and one `OpenAiCompatibleAdapter` serving
OpenAI, DeepSeek, Groq, Cerebras, Hugging Face and Mistral.

The **Play build is API-only**. Provider credentials are BYO-key and model ids are
pinned in `AiModels`, with optional per-provider overrides in Settings.

The **GitHub APK** adds a second Gemini transport. `GeminiAppBridgeAdapter`
serializes an explicit handoff to a supported installed Gemini app, shares a
redacted project snapshot plus the full IDEaz conversation and current reference
attachments, drives only the supported Gemini package through the user-enabled
accessibility service, waits for the completed response, then returns to IDEaz.
If an AI Studio key is saved, `FallbackAdapter` uses the Gemini API whenever the
installed-app route is unavailable or times out. No key is required for the
installed-app route itself.

Every mutation shares one edit contract: the AI writes into the working tree
behind an out-of-tree checkpoint, and `AiEditApprovalRequiredException` stops
anything reaching the preview until the user approves. Installed-app responses
must end in a complete unified diff before they can enter that same contract.
`IdeTools` checkpoints are durable across process death and fingerprint-gated
before restore.

## 6. Permissions and distribution boundary

The **Play** manifest contains the policy-safe common surface. It does **not**
register the external-AI accessibility service or overlay service and does not
request `SYSTEM_ALERT_WINDOW` or the special-use foreground-service permission.
The shared Settings UI hides controls for permissions that do not exist in that
build.

The **GitHub** `debug`/`release` manifests add exactly the installed-AI surface:
`SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE`, package visibility for
the supported Gemini packages, `IdeazAccessibilityService`, and
`ExternalAiOverlayService`. The accessibility XML itself is package-scoped to
those Gemini packages. The service code also ignores events unless an IDEaz
bridge request is currently in flight.

`POST_NOTIFICATIONS` remains common so long-running work can report progress while
backgrounded.

This is separate from a future native Android-target inspection loop. The
GitHub bridge drives a user-selected AI provider app; it does not inspect
arbitrary target applications.

## 7. One kind of project

There is no `ProjectType`. A directory is either previewable or it isn't, and
`ProjectAnalyzer.isPreviewable` decides by looking for an entry point.

React is the shape the pipeline is built for — JSX source metadata is what makes
a tap resolve to a file and line — and the one bundled starter is a React/Vite
app. Plain HTML still previews; it falls back to `data-ideaz-source`, then to a
selector, and the AI's preamble says which it is getting.

## 8. GitHub is optional until you publish

Create, scaffold, preview, approve and commit local work with no GitHub account.
`checkRequiredKeys` asks only for a credential actually required by the selected
AI transport: Play's hosted-provider path needs its provider key; GitHub's
installed-Gemini path can satisfy the default Gemini assignment without one.

Creating is local: scaffold from the bundled starter, `git init`, initial commit.
`RepoDelegate.ensureRemoteRepository` runs on the first **Deploy**, creates the
repository if `origin` is missing, and reuses an existing one rather than making
a second. Deploy is also the only place that reports a missing GitHub token.

The AI itself may still require network access, including the installed consumer
app. "No GitHub account" is not the same promise as "fully offline AI."

## 9. Out of scope

React Native, Flutter, Python, the on-device APK toolchain, VirtualDisplay
hosting, on-device LLM inference, and an AccessibilityService that roams arbitrary
apps as part of the Phase-1 web loop.

A future Android app as an *edit target* — remote build, sideload, inspect the
running target, prompt, rebuild — remains a separate target architecture. It is
not the installed-Gemini bridge described above.
