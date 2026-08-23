# File Descriptions

A map of what is actually here, regenerated against the tree rather than
maintained by hand. The previous version described 107 Kotlin files, 39 of which
no longer existed — `AIDelegate.kt`, `BuildDelegate.kt`, `OverlayDelegate.kt`,
`ScreenshotService.kt`, `RemoteBuildManager.kt`, the whole on-device model stack,
the Jules and Gemini-app-bridge adapters, `ProjectType.kt`, and more. An agent
orienting itself with that file would have spent its first tool calls opening
files that are not there.

For *why* the pieces fit together the way they do, read
[`architecture.md`](architecture.md). This file only says where things live.

## Root

*   `AGENTS.md` — instructions for AI agents working on IDEaz itself.
*   `README.md` — what the product is.
*   `build.gradle.kts`, `settings.gradle.kts` — Gradle build and repositories.
*   `version.properties` — four-component version source of truth. **Only ever
    edited to bump `minor` upward.**
*   `get_version.sh` — version string for CI workflows.
*   `.github/workflows/pr-check.yml` — the only pre-merge gate: JSX source-chain
    test, then `assembleDebug testDebugUnitTest desktopMainClasses lintDebug`.
*   `.github/workflows/build-and-release.yml` — builds and publishes on push to
    `master` and on manual dispatch. See `docs/build_pipeline.md` §6.
*   `.github/workflows/dependency-submission.yml` — submits the release-runtime
    dependency graph, deliberately excluding build-tool configurations.

## webruntime/

Assets-only dynamic feature module carrying the in-browser runtime. No Kotlin.

*   `src/main/assets/ideaz-runtime/ideaz-loader.js` — transpiles the project's
    JSX/TS on demand with Babel and rewrites relative imports to `blob:` URLs.
    Sets `development: true`, which is what stamps `__source` onto every element.
*   `src/main/assets/ideaz-runtime/jsx-runtime.js` — ESM shim serving both
    `react/jsx-runtime` and `react/jsx-dev-runtime` on top of `React.createElement`.
    Forwards `jsxDEV`'s `source` argument as `__source`; dropping it silently
    disabled tap-to-source for the entire product once already.
*   `src/main/assets/ideaz-runtime/*.js` — vendored React 18.3.1 (development
    build — `_debugSource` only exists there), React-DOM, Babel standalone, and
    the common ecosystem libraries the import map resolves.
*   `src/test/js/jsx-source-chain.test.mjs` — end-to-end assertion that a tap can
    still resolve to a file and line: runs the shipped Babel and React over real
    JSX through the real shim. No mocks, because a mock passed against the broken
    shim.

## app/

*   `build.gradle.kts` — KMP module: `androidTarget()` plus `jvm("desktop")`.
*   `src/androidMain/AndroidManifest.xml` — `POST_NOTIFICATIONS`, `MainActivity`,
    `CrashReportingService`. Nothing else is declared.
*   `src/androidMain/assets/ideaz-bridge.js` — injected into the preview. Collects
    DOM context for a tapped element and walks the React fiber tree for
    `_debugSource`, falling back to a `data-ideaz-source` attribute, then a
    selector.
*   `src/androidMain/assets/templates/react/` — the one bundled starter. A
    Vite-shaped React app, because that is the shape the preview pipeline is for.

### commonMain — platform-agnostic

*   `platform/Platform.kt` — `expect object Platform`. Logging, Base64, and a
    debug-build flag: the only three things that pinned otherwise-portable code
    to Android.
*   `ui/delegates/SelectionDelegate.kt` — owns select mode, the tap gesture that
    starts the edit loop. Replaced `OverlayDelegate`, which needed a
    `TYPE_APPLICATION_OVERLAY` window, a foreground service, a persistent
    notification and a MediaProjection screenshot pipeline to do the same job.
*   `ui/Dependency.kt`, `ui/ProjectMetadata.kt` — small shared models.

### jvmSharedMain — shared by both JVM targets

Where the bulk of the logic lives. `commonMain` cannot touch `java.*`, which
rules out JGit, OkHttp and Retrofit; this source set is the intermediate both
JVM targets depend on.

**AI**
*   `ai/IdeTools.kt` — the tool surface the AI acts through, plus the checkpoint
    machinery: an immutable out-of-tree snapshot taken before any mutation,
    durable across process death, fingerprint-gated before restore.
*   `ai/AiEditApproval.kt` — a pending validated edit awaiting an explicit user
    decision. Every provider shares this one contract.
*   `ai/AiEditApplier.kt` — turns a text-only reply's fenced blocks back into real
    file writes, for backends that cannot call tools.
*   `ai/ConversationalAiClient.kt` — provider-agnostic interface; callers pass the
    whole history so implementations keep multi-turn context.
*   `ai/AnthropicAdapter.kt`, `ai/OpenAiCompatibleAdapter.kt` — two of the three
    adapters. The OpenAI-compatible one serves OpenAI, DeepSeek, Groq, Cerebras,
    Hugging Face and Mistral.
*   `ai/ToolSchema.kt` — provider-neutral tool-argument description each adapter
    converts to its native schema.

**GitHub and git**
*   `api/GithubApiClient.kt`, `api/models.kt`, `api/AuthInterceptor.kt` — Retrofit
    client for the GitHub API.
*   `git/GitManager.kt` — JGit wrapper. Also raises the rejections JGit reports as
    ordinary return values (non-fast-forward push, conflicting merge).
*   `utils/GithubSecretBox.kt` — libsodium-compatible sealed box over BouncyCastle,
    so Actions can decrypt with the repository key.

**Project and state**
*   `utils/ProjectAnalyzer.kt` — `isPreviewable` and `findWebEntryPoint`. The only
    questions asked of a directory.
*   `models/IdeazProjectConfig.kt` — `.ideaz/config.json`: branch, owner,
    timestamp.
*   `models/ElementContext.kt` — the DOM context `ideaz-bridge.js` captures on tap.
*   `models/OperationState.kt` — one lifecycle for every long-running operation.
*   `ui/delegates/StateDelegate.kt` — UI state, including the checkpoint lifecycle.
*   `ui/web/WebProjectUrlUtils.kt` — the asset-loader origin, with the project
    mounted at its **root** so `/src/main.jsx` resolves.
*   `utils/RepoMapper.kt`, `utils/RepoSnapshot.kt` — the file tree and the
    flattened blob handed to models without file tools.
*   `utils/SourceContextHelper.kt`, `utils/LogSanitizer.kt`,
    `utils/OperationController.kt`, `utils/ErrorCollector.kt`,
    `utils/VersionUtils.kt` — supporting utilities.

### androidMain — the phone IDE

**Rendering**
*   `ui/web/WebProjectHost.kt` — the WebView host. Per-project storage isolation
    on switch, plain-language net-error translation, and the `Ideaz`/`IdeazBridge`
    interfaces scoped to the asset-loader origin.
*   `ui/web/WebProjectPathHandler.kt` — serves the project at the origin root.
    Project content is `no-store`; the bundled runtime is cacheable and segmented
    by `VERSION_CODE` so an upgrade cannot serve stale runtime JS.
*   `ui/web/WebViewBridge.kt` — receives `IdeazBridge.onElementTapped(json)`.

**The loop**
*   `ui/MainViewModel.kt` — the brain: preview, chat, checkpoints, git, projects.
*   `ui/ContextualChatOverlay.kt` — the panel that opens on tap: what was tapped,
    the conversation, and the approve/reject controls for the AI's edit.
*   `ui/SelectionOverlay.kt` — transparent tap-catcher while select mode is on.
*   `ui/MainScreen.kt`, `ui/IdeNavHost.kt`, `ui/IdeNavRail.kt`,
    `ui/IdeBottomSheet.kt` — shell, navigation, log ticker.
*   `ui/AiChatTab.kt`, `ui/PromptPopup.kt`, `ui/ContextlessChatInput.kt`,
    `ui/widget/PromptInputAttachmentRow.kt` — chat surfaces and attachments.

**Tools, not the workspace**
*   `ui/FileExplorerScreen.kt`, `ui/FileContentScreen.kt`, `ui/CodeEditor.kt`,
    `ui/editor/EditorViewModel.kt`, `ui/editor/EditorSetup.kt` — file browser and
    Sora editor, for when the AI gets stuck.
*   `ui/GitScreen.kt`, `ui/delegates/GitDelegate.kt` — git UI and off-main-thread
    wrapper.
*   `ui/project/SetupTab.kt`, `ui/project/CloneTab.kt`, `ui/project/LoadTab.kt`,
    `ui/delegates/RepoDelegate.kt` — create, clone, load, initialize.
*   `ui/SettingsScreen.kt`, `ui/SettingsViewModel.kt` — settings and preferences.

**Utilities**
*   `utils/AndroidKeystoreCredentialStore.kt` — AES-GCM persistence behind a
    non-exportable Keystore key, backing every provider key, the GitHub token and
    the signing passwords.
*   `utils/ProjectConfigManager.kt` — `.ideaz/` config, the Pages workflow,
    `AGENTS_SETUP.md`, `version.properties`.
*   `utils/TemplateManager.kt` — copies the bundled React starter into an empty
    project directory.
*   `utils/ProjectAssetImporter.kt` — copies a SAF-picked file into `assets/`.
*   `utils/ProjectFileObserver.kt` — watches the tree and triggers preview reloads.
*   `utils/BackupManager.kt`, `utils/SecurityUtils.kt`, `utils/CrashHandler.kt`,
    `utils/GithubIssueReporter.kt`, `utils/LogcatReader.kt` — backup, crypto,
    crash capture and reporting.
*   `services/CrashReportingService.kt` — files crashes as GitHub issues from a
    separate `:crash_reporter` process, so it survives the main one dying.
*   `ai/AiAdapterFactory.kt` — model id → adapter, wrapped in the client that
    injects the "study the project first" preamble and the repo map.
*   `ai/GeminiAdapter.kt` — the third adapter.
*   `ai/AttachmentResolver.kt` — resolves prompt attachments at submit time.

### desktopMain

*   `Main.kt` — `./gradlew :app:run`. Exists so the app can be launched and
    tested without a handset. The shared UI lands here as `commonMain` grows.
*   `platform/Platform.desktop.kt` — the desktop `actual`.
