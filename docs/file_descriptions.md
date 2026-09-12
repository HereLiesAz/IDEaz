# File Descriptions

A map of what is actually here, regenerated against the tree rather than
maintained by hand. The previous version described files that no longer existed
and omitted live distribution-specific code. An agent orienting itself here
should be able to trust that a named file is part of the current tree.

For *why* the pieces fit together the way they do, read
[`architecture.md`](architecture.md). This file only says where things live.

## Root

*   `AGENTS.md` — instructions for AI agents working on IDEaz itself.
*   `README.md` — what the product is.
*   `build.gradle.kts`, `settings.gradle.kts` — Gradle build and repositories.
    The root build also defines the GitHub `debug`/`release` versus Google Play
    `play` distribution split and mirrors the `play` build type into `:webruntime`.
*   `version.properties` — four-component version source of truth. Feature-sized
    releases advance `minor`; small fixes advance `patch`.
*   `get_version.sh` — version string for CI workflows.
*   `.github/workflows/pr-check.yml` — the pre-merge gate: JavaScript bridge tests,
    GitHub APK compile, Play bundle compile, Android unit tests, desktop classes,
    and Android lint.
*   `.github/workflows/build-and-release.yml` — GitHub-release APK channel.
*   `.github/workflows/publish-play.yml` — dedicated `bundlePlay` AAB build and
    optional Play upload.
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
*   `src/test/js/*.test.mjs` — runtime regression tests for JSX source metadata,
    import cycles, array children and bridge source priority.

## app/

*   `build.gradle.kts` — KMP module: `androidTarget()` plus `jvm("desktop")`.
*   `src/androidMain/AndroidManifest.xml` — policy-safe common manifest: base
    activity/services plus the FileProvider used for explicit external-app
    attachment sharing.
*   `src/debug/AndroidManifest.xml`, `src/release/AndroidManifest.xml` — GitHub
    distribution additions: installed-AI accessibility service, overlay service,
    package visibility, overlay and special-use foreground-service permissions.
*   `src/play/AndroidManifest.xml` — deliberately empty overlay: Play inherits only
    the common policy-safe manifest.
*   `src/{debug,release,play}/res/values/distribution.xml` — channel-specific
    resources matching the compile-time distribution flags.
*   `src/androidMain/assets/ideaz-bridge.js` — injected into the preview. Collects
    DOM context for a tapped element and walks the React fiber tree for
    `_debugSource`, falling back to a `data-ideaz-source` attribute, then a
    selector.
*   `src/androidMain/assets/templates/react/` — bundled React/Vite starter.

### commonMain — platform-agnostic

*   `platform/Platform.kt` — `expect object Platform`. Logging, Base64, and a
    debug-build flag: the only three things that pinned otherwise-portable code
    to Android.
*   `ui/delegates/SelectionDelegate.kt` — owns in-preview select mode and the tap
    gesture that starts the web edit loop.
*   `ui/Dependency.kt`, `ui/ProjectMetadata.kt` — small shared models.

### jvmSharedMain — shared by both JVM targets

Where the bulk of the logic lives. `commonMain` cannot touch `java.*`, which
rules out JGit, OkHttp and Retrofit; this source set is the intermediate both
JVM targets depend on.

**AI**
*   `ai/IdeTools.kt` — AI tool surface plus immutable out-of-tree edit checkpoints,
    review fingerprints and guarded restore.
*   `ai/AiEditApproval.kt` — a pending validated edit awaiting an explicit user
    decision. Every provider shares this one contract.
*   `ai/ConversationalAiClient.kt` — provider-agnostic conversational interface.
*   `ai/AnthropicAdapter.kt`, `ai/OpenAiCompatibleAdapter.kt` — cloud adapters;
    the OpenAI-compatible one serves OpenAI, DeepSeek, Groq, Cerebras, Hugging
    Face and Mistral.
*   `ai/ToolSchema.kt` — provider-neutral tool-argument description.
*   `utils/RepoSnapshot.kt` — redacted, bounded snapshot for providers with no
    file tools. Rejects symlinks and canonical paths outside the project root.

**GitHub and git**
*   `api/GithubApiClient.kt`, `api/models.kt`, `api/AuthInterceptor.kt` — Retrofit
    client for the GitHub API.
*   `git/GitManager.kt` — JGit wrapper.
*   `utils/GithubSecretBox.kt` — libsodium-compatible sealed box over BouncyCastle.

**Project and state**
*   `utils/ProjectAnalyzer.kt` — `isPreviewable` and `findWebEntryPoint`.
*   `models/IdeazProjectConfig.kt` — `.ideaz/config.json`.
*   `models/ElementContext.kt` — DOM context captured on tap.
*   `models/OperationState.kt` — one lifecycle for long-running operations.
*   `ui/delegates/StateDelegate.kt` — UI state, including edit-review lifecycle.
*   `ui/web/WebProjectUrlUtils.kt` — asset-loader origin and root mount rules.
*   `utils/RepoMapper.kt`, `utils/SourceContextHelper.kt`, `utils/LogSanitizer.kt`,
    `utils/OperationController.kt`, `utils/ErrorCollector.kt`, `utils/VersionUtils.kt`
    — supporting utilities.

### androidMain — the phone IDE

**Rendering**
*   `ui/web/WebProjectHost.kt` — WebView host with per-project storage isolation
    and origin-scoped JS interfaces.
*   `ui/web/WebProjectPathHandler.kt` — serves the project at the origin root.
*   `ui/web/WebViewBridge.kt` — receives DOM element context from JavaScript.

**AI routing and installed-app host**
*   `ai/AiAdapterFactory.kt` — model id → adapter. Google Play uses documented
    provider APIs; GitHub builds may route Gemini through the installed app and
    retain the API client as fallback when a key exists.
*   `ai/GeminiAdapter.kt` — Gemini API adapter.
*   `ai/AttachmentResolver.kt` — resolves prompt attachments at submit time.
*   `ai/bridge/GeminiAppBridgeAdapter.kt` — serialized installed-Gemini handoff:
    redacted repo snapshot, full conversation, reference attachments, response
    capture, unified-diff extraction, and the normal edit-review gate.
*   `ai/bridge/GeminiAppBridge.kt` — process-local bridge session state/mailbox.
*   `ai/bridge/BridgeHeuristics.kt` — version-tolerant composer/send/copy/generation
    accessibility matching.
*   `ai/bridge/ExternalAiWindowHost.kt` — persisted GitHub-only presentation mode
    (frame, compact frame, freeform, adjacent/embedded best effort, fullscreen)
    and return-to-IDEaz behavior.
*   `services/IdeazAccessibilityService.kt` — GitHub-only driver restricted by
    manifest config to the supported Gemini packages and active only for an
    in-flight bridge request.
*   `services/ExternalAiOverlayService.kt` — non-touchable IDEaz frame around the
    live external app; registered only by GitHub build manifests.
*   `res/xml/external_ai_accessibility_config.xml` — package-scoped accessibility
    event configuration for the external Gemini bridge.

**The loop**
*   `ui/MainViewModel.kt` — preview, chat, checkpoints, git and projects.
*   `ui/ContextualChatOverlay.kt` — tapped-element chat and edit approval controls.
*   `ui/SelectionOverlay.kt` — transparent in-app tap-catcher for web select mode.
*   `ui/MainScreen.kt`, `ui/IdeNavHost.kt`, `ui/IdeNavRail.kt`,
    `ui/IdeBottomSheet.kt` — shell, navigation, log ticker.
*   `ui/AiChatTab.kt`, `ui/PromptPopup.kt`, `ui/ContextlessChatInput.kt`,
    `ui/widget/PromptInputAttachmentRow.kt` — chat surfaces and attachments.

**Tools, not the workspace**
*   `ui/FileExplorerScreen.kt`, `ui/FileContentScreen.kt`, `ui/CodeEditor.kt`,
    `ui/editor/EditorViewModel.kt`, `ui/editor/EditorSetup.kt` — file browser/editor.
*   `ui/GitScreen.kt`, `ui/delegates/GitDelegate.kt` — git UI and wrapper.
*   `ui/project/SetupTab.kt`, `ui/project/CloneTab.kt`, `ui/project/LoadTab.kt`,
    `ui/delegates/RepoDelegate.kt` — create, clone, load, initialize.
*   `ui/SettingsScreen.kt`, `ui/SettingsViewModel.kt` — provider credentials,
    assignments, permissions and GitHub-only external-AI window preference.

**Utilities and services**
*   `utils/AndroidKeystoreCredentialStore.kt` — AES-GCM persistence behind a
    non-exportable Keystore key.
*   `utils/ProjectConfigManager.kt` — `.ideaz/` config, Pages workflow,
    `AGENTS_SETUP.md`, `version.properties`.
*   `utils/TemplateManager.kt` — copies the bundled React starter.
*   `utils/ProjectAssetImporter.kt` — copies a SAF-picked file into `assets/`.
*   `utils/ProjectFileObserver.kt` — watches the tree and triggers preview reloads.
*   `utils/BackupManager.kt`, `utils/SecurityUtils.kt`, `utils/CrashHandler.kt`,
    `utils/GithubIssueReporter.kt`, `utils/LogcatReader.kt` — backup, crypto,
    crash capture and reporting.
*   `services/CrashReportingService.kt` — files opted-in crashes as GitHub issues
    from a separate `:crash_reporter` process.

### desktopMain

*   `Main.kt` — `./gradlew :app:run` desktop host.
*   `platform/Platform.desktop.kt` — desktop `actual`.
