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

**The element→source problem** is the hard part, and the thing the product is a
bet on. It is solved by the preview pipeline itself: `ideaz-loader.js` transpiles
the project's own source in-browser with Babel, and does so with
`development: true`, which enables `@babel/plugin-transform-react-jsx-source`.
That stamps `{fileName, lineNumber, columnNumber}` onto every JSX element; React
exposes it on the fiber as `_debugSource`; `ideaz-bridge.js` walks the fiber tree
from the tapped node and reads it. The AI therefore receives `src/App.jsx:42`
rather than a CSS selector and a search problem.

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
AI layer (`IdeTools` and its checkpoint machinery, the three adapters, the tool
schema, the edit-approval contract), the GitHub API client, `GitManager`,
`StateDelegate`, `ProjectAnalyzer`, `RepoSnapshot`, `GithubSecretBox`.

The platform seam is deliberately tiny — one `expect object Platform` covering
logging, Base64, and a debug-build flag. Those three were all that pinned
otherwise-portable code to Android.

## 4. Rendering

`WebProjectHost` mounts the project's working tree at the origin root of
`https://appassets.androidplatform.net/` via `WebViewAssetLoader`, so it gets a
real origin and service-worker support with no network.

There is no bundler on a phone, so `webruntime/` ships a vendored JS runtime
(React, React-DOM, Babel, and the common ecosystem libraries) plus
`ideaz-loader.js`, which transpiles JSX/TS on demand and rewrites relative
imports to `blob:` URLs. This is the most inventive thing in the codebase and it
is the right answer to the constraint.

`WebProjectPathHandler` marks project content `no-store` (it changes under the
user's hands) while the bundled runtime is cacheable and version-segmented by
`BuildConfig.VERSION_CODE`, so an app upgrade cannot serve stale runtime JS.

## 5. AI providers

Eight registered providers behind three adapters: `GeminiAdapter`,
`AnthropicAdapter`, and one `OpenAiCompatibleAdapter` serving every
`/chat/completions` backend (OpenAI, DeepSeek, Groq, Cerebras, Hugging Face,
Mistral). All BYO-key.

Wire model ids are **pinned** in `AiModels` and overridable per provider in
Settings. They used to be discovered at runtime by regex-matching the provider's
`/models` listing and taking the newest by publish date, which made the choice
nondeterministic and, for OpenAI, routinely selected a non-chat variant because
the filter also matched `gpt-4o-transcribe` and `gpt-4o-audio-preview`.

Every provider shares one edit contract: the AI writes into the working tree
behind an out-of-tree checkpoint, and `AiEditApprovalRequiredException` stops
anything reaching the preview until the user approves. The checkpoint machinery
(`IdeTools`) is durable across process death, fingerprint-gated before restore,
and reconciled at startup.

## 6. Permissions

The core loop happens inside this app's own WebView and needs **none**.
`POST_NOTIFICATIONS` is the only one declared, so long-running work can report
progress while backgrounded.

Deliberately absent, and not to be re-added without a target that needs them:
`SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`,
`FOREGROUND_SERVICE_SPECIAL_USE`, `REQUEST_INSTALL_PACKAGES`, and two
accessibility services.

## 7. One kind of project

There is no `ProjectType`. A directory is either previewable or it isn't, and
`ProjectAnalyzer.isPreviewable` decides by looking for an entry point.

The enum used to have six values and cost branching in a dozen files. Five of
them were fiction: `ANDROID` drove a remote-APK pipeline and an on-device Wasm
compiler that no longer exist; `WEB`, `REACT`, `OTHER` and `UNKNOWN` were never
in `selectable`, a list whose only member was `PWA` — and the analyzer never
returned `REACT` at all, so every previewable project reported `PWA` whatever it
actually was. The taxonomy bought a label nobody could act on, and gated real
behavior on it: `ensureWorkflow` once branched on a pre-split enum member, wrote
no workflow, reported success, and polled GitHub Pages for ten minutes for a
site nothing would ever build.

React is the shape the pipeline is built for — `jsx-source` is what makes a tap
resolve to a file and line — and the one bundled starter is a React/Vite app.
Plain HTML still previews; it falls back to `data-ideaz-source`, then to a
selector, and the AI's preamble says which it is getting.

## 8. Out of scope

React Native, Flutter, Python, the on-device APK toolchain, VirtualDisplay
hosting, on-device LLM inference, driving other apps through an
AccessibilityService, and any on-device Kotlin compiler.

Android as an *edit target* (build remotely, sideload, inspect the running app)
is not implemented and nothing here is shaped to accept it back as a flag. What
went with it: the `templates/android` scaffold and its placeholder-substitution
and package-relocation machinery, `setup_env.sh` (a JDK + Android SDK bootstrap
written into every project), `ProjectInitializer`'s crash-reporter injection,
the `build.gradle(.kts)` `versionCode`/`versionName` rewriting, GitHub artifact
polling, APK version comparison and launch, and the target-package-name setting
the last four hung off. It is a different `Target`, and it would start over.
