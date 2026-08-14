# File Descriptions

> Post-Phase-0 snapshot. Many entries from earlier versions of this file described
> source files that have been deleted (React Native, Flutter, Python, on-device
> toolchain, Zipline/Redwood, VirtualDisplay host, Jules CLI, Gemini CLI). They
> are gone and not listed here.

## Root Directory
*   `AGENTS.md`: Critical instructions for AI agents.
*   `README.md`: Project overview.
*   `build.gradle.kts`: Root Gradle build script.
*   `settings.gradle.kts`: Gradle settings and repository configuration.
*   `version.properties`: Four-component source of truth for repository versions; generated projects receive the same contract.
*   `webruntime/`: Dynamic feature module for bundled web runtime assets.
*   `get_version.sh`: Script to retrieve the version string for CI/CD workflows.
*   `.gitignore`: Git ignore rules.
*   `.github/workflows/antigravity-*.yml`: Antigravity CLI dispatch, invocation, review, and issue-triage automation.
*   `.github/workflows/dependency-submission.yml`: Submits the resolved `:app` and `:webruntime` release-runtime dependency graph to GitHub; intentionally excludes test, lint, Gradle, AGP, plugin, and other build-tool configurations from the production SBOM.

## app/
*   `build.gradle.kts`: App module build script.
*   `src/main/AndroidManifest.xml`: Application manifest (Permissions, Activities, Services).
*   `src/main/assets/templates/android/version.properties`: Four-component version seed consumed by the bundled Android template and its workflow.

### app/src/main/kotlin/com/hereliesaz/ideaz/
*   `MainActivity.kt`: The main entry point and UI host.
*   `MainApplication.kt`: Application subclass for global initialization and memory-pressure release of cached local inference engines.
*   `IBuildService.aidl`: IPC interface for the Build Service.
*   `IBuildCallback.aidl`: IPC interface for Build Service callbacks.

#### api/
*   `ApiClient.kt`: Retrofit client builder.
*   `GeminiApiClient.kt`: HTTP client for Gemini API. Phase 1 wraps this in a `ConversationalAiClient` adapter.
*   `GithubApiClient.kt`: Client for GitHub API.
*   `models.kt`: Data classes for API responses.
*   `AuthInterceptor.kt`: Adds API keys to requests.
*   `LoggingInterceptor.kt`: Logs API requests/responses (sanitized).
*   `RetryInterceptor.kt`: Handles retry logic for failed requests.

#### jules/
*   `JulesApiClient.kt`: Client for Jules API. Stubbed in Phase 0; restored in Phase 2.
*   `JulesApi.kt`: Retrofit interface for Jules.
*   `IJulesApiClient.kt`: Interface definition.
*   `JulesAdapter.kt`: `AgenticAiClient` over `JulesApiClient` — owns the create/resume-session + activity-poll lifecycle and emits `TaskEvent`s (the single source of truth `AIDelegate` collects). Polls `getSession` for `outputs[].pullRequest` and emits a terminal `TaskEvent.PullRequest`.

#### buildlogic/
*   `RemoteBuildManager.kt`: Dispatches and polls remote GitHub Actions builds. The only build path that survived Phase 0.
*   `PullRequestCoordinator.kt`: Auto-merges an agent-opened PR (parse URL → `GitHubApi.mergePullRequest`) and returns the merge commit SHA for `RemoteBuildManager` to rebuild against. The "auto-merge" half of the PR-based Android loop.

#### git/
*   `GitManager.kt`: Wrapper around JGit for version control operations.

#### models/
*   `Project.kt`: Project metadata model.
*   `ProjectType.kt`: Project-type enum and production selection gate. PWA is the sole selectable target; other types remain detectable without being routed into incomplete loops.
*   `IdeazProjectConfig.kt`: Configuration model.
*   `ProjectHistory.kt`: History tracking model.

#### services/
*   `BuildService.kt`: Foreground service in `:build_process`. Post-Phase-0 it is a thin shell around `RemoteBuildManager`.
*   `IdeazAccessibilityService.kt`: Accessibility Service that captures tapped elements in the sideloaded target app — resolves the tapped node's `viewIdResourceName` + screen bounds (→ source file/line context) and reports the target app's window bounds to the overlay.
*   `IdeazOverlayService.kt`: System Alert Window overlay for Phase 2 (wired but inert until Phase 2).
*   `CrashReportingService.kt`: Service for fatal error reporting in `:crash_reporter`.
*   `ScreenshotService.kt`: `MediaProjection` virtual display for region screenshots. Declared in the manifest (`mediaProjection` FGS) and started **only for Android target projects**, gated at runtime by `OverlayDelegate.isScreenCaptureEnabled()`; web/PWA projects never raise the consent dialog. The captured PNG is attached to the contextual prompt for image-capable models (and embedded for Jules).

#### ai/
*   `AiAdapterFactory.kt`: Centralized factory that maps AI models to concrete adapters.
*   `OpenAiCompatibleAdapter.kt`: Generic adapter for OpenAI-compatible `/chat/completions` endpoints.
*   `AnthropicAdapter.kt`: Custom adapter for Anthropic's Messages API schema.
*   `DynamicModelResolver.kt`: Resolves the absolute latest version of a model by querying provider endpoints.
*   `GeminiAdapter.kt`: Uses the `google-genai` SDK for Gemini models.
*   `GeminiNanoAdapter.kt`: Specialized adapter for on-device Gemini Nano that shares the serialized, memory-pressure-aware `AiCoreRuntime` cache.
*   `ConversationalAiClient.kt`: Base interface for AI clients (Phase 1, conversational), including the documented structured exception contract used by local providers.
*   `AgenticAiClient.kt`: Phase-2 agentic provider interface — `dispatchTask(prompt, sourceContext): Flow<TaskEvent>`. Target-agnostic event stream (`SessionStarted`/`Message`/`Patch`/`TimedOut`) so the overlay renders Jules and Gemini the same way. Implemented by `jules/JulesAdapter`.
*   `IdeTools.kt`: Sandboxed AI file tools plus out-of-tree per-file content checkpoints, changed-file review, JSON validation, drift detection, and rollback without moving Git HEAD.
*   `ToolSchema.kt`: JSON schemas for tools.

#### ai/local/
*   `LocalModelRuntime.kt`: Interface and implementations for on-device backends — serialized, model-keyed engine caches for AICore, MediaPipe, llama.cpp/GGUF, and ONNX GenAI, with RAM-tier inference limits and coordinated release.
*   `LocalLlmAdapter.kt`: Conversational adapter for the selected local runtime; drives a JVM-tested six-round JSON coordinator with an explicit cancellation restoration boundary, applies device-tier prompt limits, restores interrupted mutations, and raises validated edits for explicit approval before reload.
*   `LocalModelCatalog.kt`: Curated list of downloadable on-device models, with per-model RAM/ABI/auth requirements used for filtering.
*   `DeviceCapabilities.kt`: Reads device RAM (`ActivityManager.MemoryInfo`) and supported CPU ABIs (`Build.SUPPORTED_ABIS`).
*   `LocalModelAvailability.kt`: Pure, unit-tested logic deciding whether a model is usable on this device/build (backend present, RAM, ABI, token) — drives the Settings list filtering.
*   `LocalModelStore.kt`: Persists the selected on-device model and requests cached-engine release when that selection changes.
*   `ModelDownloadManager.kt`: Downloads model files with authentication and strict range resume; copies cancellable chunks through a JVM-tested staging primitive, reconciles interrupted partials through catalog-bound sidecars, preflights remaining bytes plus a safety reserve, validates trusted exact sizes/SHA-256 values before atomic activation, and records manifest-bound verification markers.
*   `LocalModelDownloadWorker.kt`: Unique WorkManager foreground job for durable model downloads with connected-network constraints, progress, cancellation, bounded retry/backoff, and token-safe input data.

#### ai/bridge/
*   `GeminiAppBridgeAdapter.kt`: `ConversationalAiClient` that routes prompts through the user's installed Gemini app — attaches the project as `project.txt` and raises the touch-block scrim, then waits for the scraped reply.
*   `GeminiAppBridge.kt`: Process singleton mailbox between the adapter and the accessibility service (`pendingPrompt`, `isWaiting`, `phase`, `promptSubmitted`, response/decision channels).
*   `GeminiAppBridgeAccessibilityService.kt`: Drives the Gemini app — INPUT phase types the prompt into the compose field and taps Send; AWAIT_RESPONSE phase scrapes the reply (Copy button → clipboard, else text scrape).
*   `BridgeHeuristics.kt`: Pure, unit-tested predicates for matching the Gemini app's input/send/copy nodes and stripping the prompt from a scrape.

#### ui/
*   `MainViewModel.kt`: Coordinator. Logic delegated to `ui/delegates/`; chat recovery validates diagnostics, recovers interrupted local edit reviews, and performs explicit one-shot local retry or approved Gemini fallback without duplicating user turns.
*   `AiChatTab.kt`: Conversational history UI with separate provider-failure and changed-file review cards, on-device retry, edit approve/reject/undo controls, and a disclosed one-shot Gemini approval action.
*   `IdeBottomSheet.kt`: Global console/chat sheet; wires conversational history and structured provider-failure state into the Chat tab.
*   `SettingsViewModel.kt`: Manages user preferences, routes gated Hugging Face tokens through the secure credential store, migrates legacy plaintext, and includes credentials only in explicit password-encrypted export/import.
*   `MainScreen.kt`: The main Compose screen.
*   `ProjectScreen.kt`: Project management UI (Setup / Load / Clone tabs); delegates release-scope enforcement to Setup and `MainViewModel.loadProject`.
*   `IdeBottomSheet.kt`: Console / chat bottom sheet.
*   `IdeNavRail.kt`: Navigation component.
*   `AiModels.kt`: AI model selection.
*   `GitScreen.kt`: Git management UI.
*   `SettingsScreen.kt`: Settings UI, including secure Hugging Face token feedback and minimum-password validation for credential-bearing exports.
*   `FileExplorerScreen.kt`: Read/write file explorer (escape hatch).
*   `FileContentScreen.kt`: File viewer/editor (escape hatch).
*   `LibrariesScreen.kt`: Dependency management UI.
*   `CodeEditor.kt`: Compose component for code display.
*   `PromptPopup.kt`: Simple dialog for text input.
*   `OnDeviceModelsSection.kt`: Settings UI for managing on-device LLMs.
*   `SheetDetents.kt`: Bottom sheet expansion states.
*   `ContextlessChatInput.kt`: Prompt input outside element-tap context.
*   `DragIndication.kt`: Visual handle for draggable UI elements.
*   `SelectionOverlay.kt`: Selection rectangle compose layer.
*   `ContextualChatOverlay.kt`: Chat anchored to a selected region.
*   `LiveOutputBottomCard.kt`: Scrolling log stream card.
*   `theme/`: Theme definitions.

#### ui/delegates/
*   `AIDelegate.kt`: AI sessions (Phase 1 Gemini conversational; Phase 2 Jules agentic), including structured local-provider failure presentation for overlay tasks.
*   `StateDelegate.kt`: Shared UI state, including conversational history and a separate structured chat-failure channel so failures never become model turns.
*   `BuildDelegate.kt`: BuildService binding; remote build dispatch + poll + install.
*   `GitDelegate.kt`: Git operations and state.
*   `OverlayDelegate.kt`: Visual overlay and selection mode. `isScreenCaptureEnabled()` gates MediaProjection capture to Android target projects (web/PWA never prompt).
*   `RepoDelegate.kt`: GitHub repo fetch / create. `uploadProjectSecrets` is currently a no-op stub — see `docs/plans/phase-0-followups.md`.
*   `StateDelegate.kt`: Centralized shared UI state.
*   `SystemEventDelegate.kt`: BroadcastReceivers for system events.
*   `UpdateDelegate.kt`: Application self-updates.

#### ui/editor/
*   `EditorSetup.kt`: Initializes the Rosemoe Sora editor engine.
*   `JavaAnalyzer.kt`: Java syntax analysis helper.

#### ui/inspection/
*   `InspectionEvents.kt`: Events for UI inspection.
*   `OverlayCanvas.kt`: Canvas for drawing inspection overlays.
*   `OverlayView.kt`: View for handling overlay interactions.

#### ui/project/
*   `LoadTab.kt`: Project loading UI.
*   `CloneTab.kt`: Project cloning UI.
*   `SetupTab.kt`: Project creation and setup UI.
*   `WebProjectHost.kt`: Embeds Web/PWA projects via WebView. Promoted to primary host in the design; Phase 1 will add `WebViewAssetLoader` + DOM bridge.
*   (Phase 2 will reintroduce a host for the Android target on top of `IdeazOverlayService` rather than `VirtualDisplay`.)

#### utils/
*   `TemplateManager.kt`: Project template copying and customization.
*   `ProjectAnalyzer.kt`: Detects project types. Phase 1 adds PWA detection.
*   `ProjectConfigManager.kt`: Manages `.ideaz` config and Workflow Injection.
*   `ProjectInitializer.kt`: Project setup + crash reporter injection.
*   `ProcessExecutor.kt`: Helper to run shell commands.
*   `SourceContextHelper.kt`: Resolves source locations from `__source__` DOM tags emitted by Web inspect-on-tap.
*   `GithubSecretBox.kt`: Pure-JVM libsodium-compatible `crypto_box_seal` (BouncyCastle) used to encrypt GitHub Actions secrets in `RepoDelegate.uploadProjectSecrets`.
*   `ApkInstaller.kt`: Helper to install APKs (Phase 2 path).
*   `CrashHandler.kt`: JVM uncaught exception handler.
*   `GithubIssueReporter.kt`: Posts GitHub issues for IDE-internal errors.
*   `SecurityUtils.kt`: PBKDF2 encryption helpers for credentials.
*   `AndroidKeystoreCredentialStore.kt`: AES-GCM credential persistence backed by a non-exportable Android Keystore key; used for gated Hugging Face tokens.
*   `PermissionUtils.kt`: Permission check/request helpers.
*   `ComposeLifecycleHelper.kt`: Helper for ComposeView lifecycle in Services.
*   `EnvironmentSetup.kt`: Setup script constants.
*   `BackupManager.kt`: Project backup logic.
*   `ErrorCollector.kt`: Non-fatal error collection.
*   `LogcatReader.kt`: System logcat reader.

## docs/
*   `ux_userflow_audit.md`: Code-backed inventory and production-readiness assessment of every user-visible flow, with P0/P1/P2 findings, interaction standards, delivery order, and release evidence gates.
See the index in `AGENTS.md`. The current source-of-truth is `docs/plans/2026-05-01-ideaz-revival-design.md`.

## website/
*   `_config.yml`: Jekyll configuration for the project website.
*   `index.md`: The homepage content.
*   `_layouts/`: HTML templates for the site.
*   `assets/`: CSS and other static assets.
