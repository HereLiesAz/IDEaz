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
*   `version.properties`: Single Source of Truth for the project version (automatically incremented during build).
*   `get_version.sh`: Script to retrieve the version string for CI/CD workflows.
*   `.gitignore`: Git ignore rules.

## app/
*   `build.gradle.kts`: App module build script.
*   `src/main/AndroidManifest.xml`: Application manifest (Permissions, Activities, Services).

### app/src/main/kotlin/com/hereliesaz/ideaz/
*   `MainActivity.kt`: The main entry point and UI host.
*   `MainApplication.kt`: Application subclass for global initialization.
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
*   `JulesAdapter.kt`: `AgenticAiClient` over `JulesApiClient` — owns the create/resume-session + activity-poll lifecycle and emits `TaskEvent`s (the single source of truth `AIDelegate` collects).

#### buildlogic/
*   `RemoteBuildManager.kt`: Dispatches and polls remote GitHub Actions builds. The only build path that survived Phase 0.

#### git/
*   `GitManager.kt`: Wrapper around JGit for version control operations.

#### models/
*   `Project.kt`: Project metadata model.
*   `ProjectType.kt`: Enum for supported project types (currently `ANDROID`, `WEB`; Phase 1 adds `PWA` detection).
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
*   `GeminiNanoAdapter.kt`: Specialized adapter for on-device Gemini Nano.
*   `ConversationalAiClient.kt`: Base interface for AI clients (Phase 1, conversational).
*   `AgenticAiClient.kt`: Phase-2 agentic provider interface — `dispatchTask(prompt, sourceContext): Flow<TaskEvent>`. Target-agnostic event stream (`SessionStarted`/`Message`/`Patch`/`TimedOut`) so the overlay renders Jules and Gemini the same way. Implemented by `jules/JulesAdapter`.
*   `IdeTools.kt`: Definitions and dispatcher for IDE tools available to the AI.
*   `ToolSchema.kt`: JSON schemas for tools.

#### ai/local/
*   `LocalModelRuntime.kt`: Interface and implementations for on-device backends — AICore + MediaPipe (wired) and llama.cpp/GGUF + ONNX GenAI (reflection-driven `generate()`, active once their library is on the classpath).
*   `LocalModelCatalog.kt`: Curated list of downloadable on-device models, with per-model RAM/ABI/auth requirements used for filtering.
*   `DeviceCapabilities.kt`: Reads device RAM (`ActivityManager.MemoryInfo`) and supported CPU ABIs (`Build.SUPPORTED_ABIS`).
*   `LocalModelAvailability.kt`: Pure, unit-tested logic deciding whether a model is usable on this device/build (backend present, RAM, ABI, token) — drives the Settings list filtering.
*   `LocalModelStore.kt`: Manages locally stored model files and metadata.
*   `ModelDownloadManager.kt`: Handles background downloading of model files with auth support.

#### ai/bridge/
*   `GeminiAppBridgeAdapter.kt`: `ConversationalAiClient` that routes prompts through the user's installed Gemini app — attaches the project as `project.txt` and raises the touch-block scrim, then waits for the scraped reply.
*   `GeminiAppBridge.kt`: Process singleton mailbox between the adapter and the accessibility service (`pendingPrompt`, `isWaiting`, `phase`, `promptSubmitted`, response/decision channels).
*   `GeminiAppBridgeAccessibilityService.kt`: Drives the Gemini app — INPUT phase types the prompt into the compose field and taps Send; AWAIT_RESPONSE phase scrapes the reply (Copy button → clipboard, else text scrape).
*   `BridgeHeuristics.kt`: Pure, unit-tested predicates for matching the Gemini app's input/send/copy nodes and stripping the prompt from a scrape.

#### ui/
*   `MainViewModel.kt`: Coordinator. Logic delegated to `ui/delegates/`.
*   `SettingsViewModel.kt`: Manages user preferences.
*   `MainScreen.kt`: The main Compose screen.
*   `ProjectScreen.kt`: Project management UI (Setup / Load / Clone tabs).
*   `IdeBottomSheet.kt`: Console / chat bottom sheet.
*   `IdeNavRail.kt`: Navigation component.
*   `AiModels.kt`: AI model selection.
*   `GitScreen.kt`: Git management UI.
*   `SettingsScreen.kt`: Settings UI.
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
*   `AIDelegate.kt`: AI sessions (Phase 1 Gemini conversational; Phase 2 Jules agentic).
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
*   `PermissionUtils.kt`: Permission check/request helpers.
*   `ComposeLifecycleHelper.kt`: Helper for ComposeView lifecycle in Services.
*   `EnvironmentSetup.kt`: Setup script constants.
*   `BackupManager.kt`: Project backup logic.
*   `ErrorCollector.kt`: Non-fatal error collection.
*   `LogcatReader.kt`: System logcat reader.

## docs/
See the index in `AGENTS.md`. The current source-of-truth is `docs/plans/2026-05-01-ideaz-revival-design.md`.

## website/
*   `_config.yml`: Jekyll configuration for the project website.
*   `index.md`: The homepage content.
*   `_layouts/`: HTML templates for the site.
*   `assets/`: CSS and other static assets.
