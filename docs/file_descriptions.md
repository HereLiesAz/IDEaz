# File Descriptions

## Root Directory
*   `AGENTS.md`: Critical instructions for AI agents.
*   `README.md`: Project overview.
*   `build.gradle.kts`: Root Gradle build script.
*   `settings.gradle.kts`: Gradle settings and repository configuration.
*   `setup_env.sh`: Script to set up the development environment (Java, Android SDK).
*   `version.properties`: Single Source of Truth for the project version.
*   `get_version.sh`: Script to retrieve the version string for CI/CD workflows.

## app/
*   `build.gradle.kts`: App module build script.
*   `src/main/AndroidManifest.xml`: Application manifest (Permissions, Activities, Services).

### app/src/main/kotlin/com/hereliesaz/ideaz/
*   `MainActivity.kt`: The main entry point and UI host.
*   `MainApplication.kt`: Application subclass for global initialization.

#### api/
*   `ApiClient.kt`: Retrofit client builder.
*   `GeminiApiClient.kt`: Client for Gemini API.
*   `GithubApiClient.kt`: Client for GitHub API.
*   `JulesApiClient.kt`: Client for Jules API.
*   `JulesCliClient.kt`: Wrapper for the local `jules` CLI binary.
*   `models.kt`: Data classes for API responses.

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

#### git/
*   `GitManager.kt`: Wrapper around JGit for version control operations.

#### services/
*   `BuildService.kt`: Background service running the build toolchain.
*   `UIInspectionService.kt`: Accessibility Service for UI inspection and overlay.
*   `IdeazOverlayService.kt`: Foreground Service for the main visual overlay. Extends `AzNavRailOverlayService`.
*   `ScreenshotService.kt`: Service for capturing screenshots.
*   `IBuildService.aidl`: IPC interface for the Build Service.

#### ui/
*   `MainViewModel.kt`: Central logic for the UI, state management, and orchestration.
*   `SettingsViewModel.kt`: Manages user preferences.
*   `MainScreen.kt`: The main Compose screen.
*   `ProjectScreen.kt`: Project management UI (Load/Create).
*   `IdeBottomSheet.kt`: The global log and chat console.
*   `IdeNavRail.kt`: Navigation component.

#### ui/delegates/
*   `AIDelegate.kt`: Manages AI sessions and Jules interaction.
*   `BuildDelegate.kt`: Manages BuildService binding and execution.
*   `GitDelegate.kt`: Manages Git operations and state.
*   `OverlayDelegate.kt`: Manages the visual overlay and selection mode.
*   `RepoDelegate.kt`: Manages repository fetching and creation.
*   `StateDelegate.kt`: Centralizes shared UI state (logs, progress).
*   `SystemEventDelegate.kt`: Handles BroadcastReceivers for system events.
*   `UpdateDelegate.kt`: Handles application self-updates.

#### utils/
*   `ToolManager.kt`: Installs and locates native tools (`aapt2`, `java`, etc.).
*   `ProjectAnalyzer.kt`: Detects project types and configurations.
*   `ProcessExecutor.kt`: Helper to run native shell commands.
*   `SourceMapParser.kt`: Parses R8 mapping files.
*   `SourceContextHelper.kt`: Resolves source locations from view IDs.
*   `ApkInstaller.kt`: Helper to install APKs.
*   `PermissionUtils.kt`: Helper for checking and requesting storage permissions.

## docs/
(See `AGENTS.md` for index)

## website/
*   `_config.yml`: Jekyll configuration for the project website.
*   `_layouts/`: HTML templates for the site.
*   `assets/`: CSS and other static assets.
*   `index.md`: The homepage content.
