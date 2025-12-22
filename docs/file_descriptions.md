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
*   `JulesCliClient.kt`: Wrapper for the local `jules` CLI binary (Legacy/Reference).
*   `models.kt`: Data classes for API responses.
*   `AuthInterceptor.kt`: Adds API keys to requests.
*   `LoggingInterceptor.kt`: Logs API requests/responses.
*   `RetryInterceptor.kt`: Handles retry logic for failed requests.

#### jules/
*   `JulesApiClient.kt`: Client for Jules API (Agentic Interface).
*   `JulesApi.kt`: Retrofit Interface for Jules.
*   `IJulesApiClient.kt`: Interface definition.

#### buildlogic/
*   `BuildOrchestrator.kt`: Manages the execution of build steps.
*   `BuildCacheManager.kt`: Manages caching of build outputs to skip redundant work.
*   `BuildStep.kt`: Interface for individual build actions.
*   `Aapt2Compile.kt` / `Aapt2Link.kt`: Android resource compilation.
*   `KotlincCompile.kt`: Kotlin source compilation.
*   `JavaCompile.kt`: Java source compilation.
*   `D8Compile.kt`: Dex compilation.
*   `ApkBuild.kt` / `ApkSign.kt`: APK packaging and signing.
*   `HttpDependencyResolver.kt`: Resolves and downloads Maven dependencies.
*   `WebBuildStep.kt`: Builds Web projects.
*   `ReactNativeBuildStep.kt`: Build step for React Native bundling.
*   `SimpleJsBundler.kt`: Basic bundler for React Native projects.
*   `ProcessManifest.kt`: Manifest merging/processing.
*   `ProcessAars.kt`: AAR extraction and resource processing.
*   `GenerateSourceMap.kt`: Generates source maps for UI inspection.
*   `PythonInjector.kt`: Injects Python runtime assets.
*   `ScalaCompile.kt`: Scala compilation support (Experimental).
*   `SmaliCompile.kt` / `BaksmaliDecompile.kt`: Smali toolchain steps.
*   `RemoteBuildManager.kt`: Manages remote build execution.
*   `RedwoodCodegen.kt`, `ZiplineCompile.kt`, `ZiplineManifestGenerator.kt`, `ZiplineManifestStep.kt`: Hybrid Host toolchain steps.

#### git/
*   `GitManager.kt`: Wrapper around JGit for version control operations.

#### models/
*   `Project.kt`: Project metadata model.
*   `ProjectType.kt`: Enum for supported project types.
*   `SourceMapEntry.kt`: Model for source mapping.
*   `IdeazProjectConfig.kt`: Configuration model.
*   `ProjectHistory.kt`: History tracking model.

#### services/
*   `BuildService.kt`: Background service running the build toolchain.
*   `IdeazAccessibilityService.kt`: Accessibility Service for UI inspection (node info retrieval).
*   `IdeazOverlayService.kt`: Foreground Service for the "Selection Overlay" (System Alert Window) allowing UI inspection and interaction.
*   `CrashReportingService.kt`: Service for reporting fatal/non-fatal errors to AI/GitHub.
*   `ScreenshotService.kt`: Service for capturing screenshots.

#### ui/
*   `MainViewModel.kt`: Central logic for the UI, state management, and orchestration.
*   `SettingsViewModel.kt`: Manages user preferences.
*   `MainScreen.kt`: The main Compose screen.
*   `ProjectScreen.kt`: Project management UI.
*   `IdeBottomSheet.kt`: The global log and chat console.
*   `IdeNavRail.kt`: Navigation component.
*   `AiModels.kt`: Enum/Object for AI model selection.
*   `GitScreen.kt`: Git management UI.
*   `SettingsScreen.kt`: Settings UI.
*   `FileExplorerScreen.kt`: Developer tool for file browsing.
*   `FileContentScreen.kt`: Developer tool for file viewing/editing.
*   `LibrariesScreen.kt`: Dependency management UI.
*   `CodeEditor.kt`: Compose component for code display.
*   `theme/`: Theme definitions (Color, Type, Theme).

#### react/
*   `ReactNativeActivity.kt`: Runner activity for React Native projects.
*   `IdeazReactPackage.kt`: Exposes native modules to RN.
*   `IdeazNativeModule.kt`: The native module implementation.

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
*   `LoadTab.kt`, `CloneTab.kt`, `SetupTab.kt`, `CreateTab.kt`: Sub-screens for ProjectScreen.
*   `AndroidProjectHost.kt`: Embeds the target Android app via VirtualDisplay.
*   `WebProjectHost.kt`: Embeds Web projects via WebView.

#### utils/
*   `ToolManager.kt`: Installs and locates build tools (downloaded to `filesDir/local_build_tools`).
*   `HybridToolchainManager.kt`: Manages Redwood/Zipline toolchain dependencies.
*   `TemplateManager.kt`: Manages project template copying and customization.
*   `ProjectAnalyzer.kt`: Detects project types and configurations.
*   `ProjectConfigManager.kt`: Manages `.ideaz` config and Workflow Injection.
*   `ProjectInitializer.kt`: Handles project setup and crash reporter injection.
*   `ProcessExecutor.kt`: Helper to run native shell commands.
*   `SourceMapParser.kt`: Parses R8 mapping files (or custom map).
*   `SourceContextHelper.kt`: Resolves source locations from view IDs.
*   `ApkInstaller.kt`: Helper to install APKs.
*   `CrashHandler.kt`: JVM uncaught exception handler.
*   `GithubIssueReporter.kt`: Utility to post GitHub issues.
*   `SecurityUtils.kt`: Encryption/Decryption helpers.
*   `PermissionUtils.kt`: Helper for checking and requesting storage permissions.
*   `ComposeLifecycleHelper.kt`: Helper for managing ComposeView lifecycle in Services.
*   `EnvironmentSetup.kt`: Contains setup script constants.
*   `BackupManager.kt`: Handles project backup logic.
*   `DependencyManager.kt`: Manages dependency resolution state.
*   `ErrorCollector.kt`: Collects non-fatal errors.
*   `LogcatReader.kt`: Utility to read system logcat.

## docs/
(See `AGENTS.md` for index)

## website/
*   `_config.yml`: Jekyll configuration for the project website.
*   `index.md`: The homepage content.
*   `_layouts/`: HTML templates for the site.
*   `assets/`: CSS and other static assets.
