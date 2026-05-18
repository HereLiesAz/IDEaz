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
*   `IdeazAccessibilityService.kt`: Accessibility Service for Phase 2 element capture (wired but inert until Phase 2).
*   `IdeazOverlayService.kt`: System Alert Window overlay for Phase 2 (wired but inert until Phase 2).
*   `CrashReportingService.kt`: Service for fatal error reporting in `:crash_reporter`.
*   `ScreenshotService.kt`: `MediaProjection` virtual display for region screenshots.

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
*   `OverlayDelegate.kt`: Visual overlay and selection mode (Phase 2).
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
