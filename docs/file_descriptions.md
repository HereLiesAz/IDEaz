# File Descriptions

## Root Directory
*   `AGENTS.md`: Critical instructions for AI agents.
*   `README.md`: Project overview.
*   `build.gradle.kts`: Root Gradle build script.
*   `settings.gradle.kts`: Gradle settings and repository configuration.
*   `setup_env.sh`: Script to set up the development environment (Java, Android SDK).
*   `version.properties`: Single Source of Truth for the project version.
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
*   `GeminiApiClient.kt`: Client for Gemini API.
*   `GithubApiClient.kt`: Client for GitHub API.
*   `JulesApiClient.kt`: Client for Jules API.
*   `JulesCliClient.kt`: Wrapper for the local `jules` CLI binary (Legacy/Reference).
*   `models.kt`: Data classes for API responses.
*   `AuthInterceptor.kt`: Adds API keys to requests.
*   `LoggingInterceptor.kt`: Logs API requests/responses.
*   `RetryInterceptor.kt`: Handles retry logic for failed requests.

#### buildlogic/
*   `BuildOrchestrator.kt`: Manages the execution of build steps.
*   `BuildCacheManager.kt`: Manages caching of build outputs to skip redundant work.
*   `BuildStep.kt`: Interface for individual build actions.
*   `Aapt2Compile.kt` / `Aapt2Link.kt`: Android resource compilation.
*   `KotlincCompile.kt`: Kotlin source compilation.
*   `D8Compile.kt`: Dex compilation.
*   `ApkBuild.kt` / `ApkSign.kt`: APK packaging and signing.
*   `HttpDependencyResolver.kt`: Resolves and downloads Maven dependencies.
*   `WebBuildStep.kt`: Builds Web projects.
*   `SimpleJsBundler.kt`: Basic bundler for React Native projects.
*   `ProcessManifest.kt`: Manifest merging/processing.
*   `ProcessAars.kt`: AAR extraction and resource processing.
*   `GenerateSourceMap.kt`: Generates source maps for UI inspection.

#### git/
*   `GitManager.kt`: Wrapper around JGit for version control operations.

#### models/
*   `Project.kt`: Project metadata model.
*   `ProjectType.kt`: Enum for supported project types.
*   `SourceMapEntry.kt`: Model for source mapping.

#### services/
*   `BuildService.kt`: Background service running the build toolchain.
*   `UIInspectionService.kt` / `IdeazAccessibilityService.kt`: Accessibility Service for UI inspection (node info).
*   `IdeazOverlayService.kt`: Foreground Service for the main visual overlay (NavRail, Console). Extends `AzNavRailOverlayService`.
*   `CrashReportingService.kt`: Service for reporting fatal/non-fatal errors to AI/GitHub.
*   `ScreenshotService.kt`: Service for capturing screenshots.

#### ui/
*   `MainViewModel.kt`: Central logic for the UI, state management, and orchestration.
*   `SettingsViewModel.kt`: Manages user preferences.
*   `MainScreen.kt`: The main Compose screen.
*   `ProjectScreen.kt`: Project management UI (Load/Create/Clone).
*   `IdeBottomSheet.kt`: The global log and chat console.
*   `IdeNavRail.kt`: Navigation component.
*   `AiModels.kt`: Enum/Object for AI model selection.
*   `GitScreen.kt`: Git management UI.
*   `SettingsScreen.kt`: Settings UI.
*   `theme/`: Theme definitions (Color, Type, Theme).

#### ui/delegates/
*   `AIDelegate.kt`: Manages AI sessions and Jules interaction.
*   `BuildDelegate.kt`: Manages BuildService binding and execution.
*   `GitDelegate.kt`: Manages Git operations and state.
*   `OverlayDelegate.kt`: Manages the visual overlay and selection mode.
*   `RepoDelegate.kt`: Manages repository fetching and creation.
*   `StateDelegate.kt`: Centralizes shared UI state (logs, progress).
*   `SystemEventDelegate.kt`: Handles BroadcastReceivers for system events.
*   `UpdateDelegate.kt`: Handles application self-updates.

#### ui/project/
*   `ProjectLoadTab.kt`, `ProjectCloneTab.kt`, `ProjectSetupTab.kt`: Sub-screens for ProjectScreen.
*   `AndroidProjectHost.kt`: Embeds the target Android app via VirtualDisplay.
*   `WebProjectHost.kt`: Embeds Web projects via WebView.

#### utils/
*   `ToolManager.kt`: Installs and locates native tools (`aapt2`, `java`, etc.).
*   `TemplateManager.kt`: Manages project template copying and customization.
*   `ProjectAnalyzer.kt`: Detects project types and configurations.
*   `ProcessExecutor.kt`: Helper to run native shell commands.
*   `SourceMapParser.kt`: Parses R8 mapping files (or custom map).
*   `SourceContextHelper.kt`: Resolves source locations from view IDs.
*   `ApkInstaller.kt`: Helper to install APKs.
*   `CrashHandler.kt`: JVM uncaught exception handler.
*   `GithubIssueReporter.kt`: Utility to post GitHub issues.
*   `SecurityUtils.kt`: Encryption/Decryption helpers.
*   `PermissionUtils.kt`: Helper for checking and requesting storage permissions.
*   `ComposeLifecycleHelper.kt`: Helper for managing ComposeView lifecycle in Services.

## docs/
(See `AGENTS.md` for index)

## website/
*   `_config.yml`: Jekyll configuration for the project website.
*   `index.md`: The homepage content.
*   `_layouts/`: HTML templates for the site.
*   `assets/`: CSS and other static assets.

## jniLibs/ (app/src/main/jniLibs)
*   `arm64-v8a/`: Native binaries for on-device build tools (renamed as `.so`).
    *   `libaapt2.so`: aapt2 binary.
    *   `libkotlinc.so`: kotlinc binary.
    *   `libd8.so`: d8 binary.
    *   `libapksigner.so`: apksigner.jar (wrapped or binary).
    *   `libjava.so`: JDK 17 java binary.
    *   `libjules.so`: Node.js runtime for Jules CLI (Legacy).
    *   `libgemini.so`: Gemini CLI binary.
