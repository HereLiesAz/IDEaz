# Manifest: Screen Component Listing and Backend Infrastructure

---

## I. MainScreen (com.hereliesaz.ideaz.ui.MainScreen)

* **Scaffold**
    * Description (Looks Like): Full-screen container with the theme's background color.
    * Description (Does): Provides the base layout structure for the entire screen.
    * Conditions (Applies To): Always present.
* **Cancel Task Dialog (AlertDialog)**
    * Description (Looks Like): Modal pop-up with title "Cancel Task," a confirmation message, and "Confirm"/"Dismiss" buttons (`AzButton`).
    * Description (Does): Confirms or cancels an active AI task, resetting progress on confirmation.
    * Conditions (Applies To): `viewModel.showCancelDialog` is true.
* **Prompt Popup (Legacy)**
    * Description (Looks Like): An `AlertDialog` (the old popup implementation).
    * Description (Does): Collects a simple text prompt and sends it to the ViewModel.
    * Conditions (Applies To): `showPromptPopup` is true.
* **IdeNavRail**
    * Description (Looks Like): A vertical navigation strip on the left side of the screen.
    * Description (Does): Hosts primary navigation links and mode controls.
    * Conditions (Applies To): Always present.
* **IDE Content (NavHost)**
    * Description (Looks Like): The main content area that displays the current screen (MainIdeScreen, ProjectScreen, or SettingsScreen).
    * Description (Does): Handles navigation between the application's primary views.
    * Conditions (Applies To): `isIdeVisible` is true (Sheet is up OR current route is "settings" OR current route is "project_settings").
* **IdeBottomSheet**
    * Description (Looks Like): A bottom-anchored, drag-responsive sheet.
    * Description (Does): Contains the `LiveOutputBottomCard` to display build and AI logs.
    * Conditions (Applies To): `isBottomSheetVisible` is true (Current route is "main" or "build").
* **ContextlessChatInput**
    * Description (Looks Like): A horizontal text input field fixed to the bottom of the screen, overlaid on the sheet's peek space.
    * Description (Does): Accepts user text input and sends it as a prompt to the ViewModel.
    * Conditions (Applies To): `isChatVisible` is true (Sheet detent is `Peek` or `Halfway`).

---

## II. IdeNavRail (com.hereliesaz.ideaz.ui.IdeNavRail)

* **Project Item (`azRailItem`)**
    * Description (Looks Like): Button labeled "Project" with a rectangular shape.
    * Description (Does): Navigates to the "project_settings" route.
    * Conditions (Applies To): Always present.
* **IDE Host Item (`azRailHostItem`)**
    * Description (Looks Like): Button labeled "IDE".
    * Description (Does): Navigates back to the main development placeholder screen.
    * Conditions (Applies To): Always present.
* **Prompt Sub-Item (`azRailSubItem`)**
    * Description (Looks Like): Button labeled "Prompt" nested under "IDE".
    * Description (Does): Calls the lambda to show the old `PromptPopup`.
    * Conditions (Applies To): Always present.
* **Build Sub-Item (`azRailSubItem`)**
    * Description (Looks Like): Button labeled "Build" nested under "IDE".
    * Description (Does): Navigates to the "build" route and expands the bottom sheet to the `Halfway` detent.
    * Conditions (Applies To): Always present.
* **Mode Toggle (`azRailSubToggle`)**
    * Description (Looks Like): Button labeled "Interact" (when active) or "Select" (when inactive) with no shape.
    * Description (Does): Toggles the IDE visibility/inspection mode. If switching to "Select" (sheet up), it requests screen capture permission first if not granted.
    * Conditions (Applies To): Always present, nested under "IDE".
* **Settings Item (`azRailItem`)**
    * Description (Looks Like): Button labeled "Settings".
    * Description (Does): Navigates to the "settings" route.
    * Conditions (Applies To): Always present.

---

## III. MainIdeScreen (com.hereliesaz.ideaz.ui.MainIdeScreen)

* **Placeholder Text**
    * Description (Looks Like): Text element displaying "Main IDE Screen," centered in the box.
    * Description (Does): Placeholder for the main code or editor interface.
    * Conditions (Applies To): When the NavHost is on route "main".

---

## IV. ProjectScreen (com.hereliesaz.ideaz.ui.ProjectScreen)

* **PrimaryTabRow**
    * Description (Looks Like): Tab selector with "Setup," "Clone," and "Load" tabs.
    * Description (Does): Switches the displayed content column.
    * Conditions (Applies To): Always present.
* **Setup Tab Content**
    * **Project Configuration Form (`AzForm`)**
        * Description (Looks Like): Form with input entries for "appName," "githubUser," "branchName," "packageName," and "initialPrompt." It includes a "Build" submit button.
        * Description (Does): Saves the project configuration to the ViewModel/Settings and sends the "initialPrompt" to start an AI task.
        * Conditions (Applies To): Tab Index is 0.
    * **"Save Template" Button (`AzButton`)**
        * Description (Looks Like): Button labeled "Save Template" with `NONE` shape.
        * Description (Does): Initiates a standard build and installation of the template project (`viewModel.startBuild`).
        * Conditions (Applies To): Tab Index is 0.
* **Clone Tab Content**
    * **Clone URL Input and "Fork" Button**
        * Description (Looks Like): `TextField` for a GitHub URL next to an "Fork" button.
        * Description (Does): Allows pasting a GitHub URL and attempts a local 'fork' operation.
        * Conditions (Applies To): Tab Index is 1.
    * **Repositories Header and Refresh Icon**
        * Description (Looks Like): Headline text "Repositories" next to a refresh icon button.
        * Description (Does): The icon button calls `viewModel.fetchOwnedSources()` to update the list of repositories from the Jules account.
        * Conditions (Applies To): Tab Index is 1.
    * **Owned Repositories List (Buttons)**
        * Description (Looks Like): A list of full-width buttons showing `owner/repo` and branch name.
        * Description (Does): Loads the selected repository's configuration into the Setup fields and switches to the Setup tab.
        * Conditions (Applies To): Tab Index is 1 and `ownedSources` is not empty.
    * **"No other repositories found..." Text**
        * Description (Looks Like): Standard body text.
        * Description (Does): Provides feedback when no repositories are linked.
        * Conditions (Applies To): Tab Index is 1 and `ownedSources` is empty.
* **Load Tab Content**
    * **Loadable Projects List (`LazyColumn`)**
        * Description (Looks Like): A scrollable list of clickable project strings (`user/repo`).
        * Description (Does): Clicking a project calls `viewModel.loadProjectAndBuild` to load the configuration and start a build.
        * Conditions (Applies To): Tab Index is 2 and `loadableProjects` is not empty.
    * **"No saved projects found." Text**
        * Description (Looks Like): Standard body text.
        * Description (Does): Provides feedback when the project list is empty.
        * Conditions (Applies To): Tab Index is 2 and `loadableProjects` is empty.

---

## V. SettingsScreen (com.hereliesaz.ideaz.ui.SettingsScreen)

* **Scrollable Column with Haze Effect**
    * Description (Looks Like): A vertical scroll container with a translucent blur effect applied to its background.
    * Description (Does): Contains all configuration options.
    * Conditions (Applies To): Always present.
* **API Keys Section Title**
    * Description (Looks Like): Large text "API Keys".
    * Description (Does): Section header.
    * Conditions (Applies To): Always present.
* **Jules API Key Input (`AzTextBox`)**
    * Description (Looks Like): A secret text field and a "Save" button.
    * Description (Does): Saves the input as the Jules API key.
    * Conditions (Applies To): Always present.
* **"Get Key" Button (Jules)**
    * Description (Looks Like): Button labeled "Get Key".
    * Description (Does): Opens a web browser to the Jules settings page.
    * Conditions (Applies To): Always present.
* **AI Studio API Key Input (`AzTextBox`)**
    * Description (Looks Like): A secret text field and a "Save" button.
    * Description (Does): Saves the input as the AI Studio API key.
    * Conditions (Applies To): Always present.
* **"Get Key" Button (AI Studio)**
    * Description (Looks Like): Button labeled "Get Key".
    * Description (Does): Opens a web browser to the AI Studio API keys page.
    * Conditions (Applies To): Always present.
* **AI Assignments Section Title**
    * Description (Looks Like): Large text "AI Assignments".
    * Description (Does): Section header.
    * Conditions (Applies To): Always present.
* **AI Assignment Dropdowns (Multiple)**
    * Description (Looks Like): Dropdowns with models like "Jules" and "Gemini".
    * Description (Does): Allows the user to fine-tune the source of their digital prophecy.
    * Conditions (Applies To): One is present for each task defined in `SettingsViewModel.aiTasks`.
* **Permissions Section Title**
    * Description (Looks Like): Large text "Permissions".
    * Description (Does): Section header.
    * Conditions (Applies To): Always present.
* **Permission Check Rows (5x)**
    * Description (Looks Like): A row for each of the five critical permissions (Draw Over Other Apps, Accessibility Service, Post Notifications, Install Unknown Apps, Screen Capture) with the name and a `Switch`.
    * Description (Does): Displays the current permission status. Clicking the switch navigates to the necessary system settings to grant the permission. The switch is disabled if permission is granted.
    * Conditions (Applies To): Always present.
* **Preferences Section Title**
    * Description (Looks Like): Large text "Preferences".
    * Description (Does): Section header.
    * Conditions (Applies To): Always present.
* **Show Cancel Warning Checkbox**
    * Description (Looks Like): A `Checkbox` labeled "Show warning when cancelling AI task".
    * Description (Does): Toggles the preference for displaying the cancellation confirmation dialog.
    * Conditions (Applies To): Always present.
* **Theme Dropdown**
    * Description (Looks Like): A menu for selecting "Automatic," "Dark Mode," "Light Mode," etc.
    * Description (Does): Selects the application's color theme and triggers the theme change callback.
    * Conditions (Applies To): Always present.
* **Log Level Dropdown**
    * Description (Looks Like): A menu for selecting Info, Debug, or Verbose.
    * Description (Does): Sets the verbosity of the in-app log output.
    * Conditions (Applies To): Always present.
* **Debug Section Title**
    * Description (Looks Like): Large text "Debug".
    * Description (Does): Section header.
    * Conditions (Applies To): Always present.
* **"Clear Build Caches" Button**
    * Description (Looks Like): Full-width button labeled "Clear Build Caches" with `RECTANGLE` shape.
    * Description (Does): Executes `viewModel.clearBuildCaches` to delete local build artifacts.
    * Conditions (Applies To): Always present.

---

## VI. IdeBottomSheet & LiveOutputBottomCard (Log UI)

* **Drag Indication**
    * Description (Looks Like): A small horizontal line centered at the top of the sheet.
    * Description (Does): A visual indicator that the sheet is draggable to change its detent.
    * Conditions (Applies To): Always visible at the top of the content area within the sheet.
* **Live Log Display (`LazyColumn`)**
    * Description (Looks Like): A scrollable column displaying lines of text (`logMessages`) in a small font.
    * Description (Does): Displays a filtered, real-time stream of build and AI log information. Automatically scrolls to the bottom.
    * Conditions (Applies To): Always visible inside the sheet.
* **"Copy Log" Icon Button**
    * Description (Looks Like): A content copy icon, located at the top right of the sheet content.
    * Description (Does): Copies the entire visible log content to the clipboard.
    * Conditions (Applies To): Only visible when the sheet is at the `Halfway` detent.
* **"Clear Log" Icon Button**
    * Description (Looks Like): A clear icon, located at the top right next to the copy button.
    * Description (Does): Clears the displayed log output by calling `viewModel.clearLog()`.
    * Conditions (Applies To): Only visible when the sheet is at the `Halfway` detent.

---

## VII. Invisible Backend Infrastructure

### A. ViewModels and State Management

* **Class: MainViewModel (AndroidViewModel)**
    * Description (Looks Like): No visible component.
    * Description (Does): Centralizes application logic, manages connection to the `BuildService`, handles AI prompt submission (contextual and contextless), coordinates the screenshot and inspection workflow, and contains project management logic (load, clone).
    * Conditions (Applies To): Requires `Application` context and `SettingsViewModel`.
* **Class: SettingsViewModel (AndroidViewModel)**
    * Description (Looks Like): No visible component.
    * Description (Does): Manages all persistent application settings including API keys, AI model assignments, log verbosity level, theme mode, and project configuration data, persisted via `SharedPreferences`.
    * Conditions (Applies To): Loaded on application startup.
* **Property: buildLog**
    * Description (Looks Like): A raw string state flow.
    * Description (Does): Stores raw log output generated by the `BuildService` via the `IBuildCallback`.
    * Conditions (Applies To): Updated whenever the build service reports a message, success, or failure.
* **Property: filteredLog**
    * Description (Looks Like): A read-only state flow derived from others.
    * Description (Does): Combines `buildLog` and `aiLog` and filters the combined stream according to the user's current `logLevel` setting (`INFO`, `DEBUG`, or `VERBOSE`).
    * Conditions (Applies To): Changes whenever a source log or the `logLevel` setting changes.

### B. Services and Inter-Process Communication (IPC)

* **Class: BuildService (Service)**
    * Description (Looks Like): Runs as a background service with a foreground notification.
    * Description (Does): Executes the multi-step Android application build pipeline in a separate process. It binds tools, sets up directories, runs the build steps via `BuildOrchestrator`, and reports the final result and APK path.
    * Conditions (Applies To): Runs as a foreground service. Bound by `MainViewModel` via AIDL.
* **Class: UIInspectionService (AccessibilityService)**
    * Description (Looks Like): Runs as a background service; draws a transparent touch-intercepting overlay when active.
    * Description (Does): Uses Android Accessibility APIs to capture user taps/drags on the screen, identifies UI nodes/areas (Resource ID and bounds), and sends the resulting data back to the `MainViewModel` for contextual prompting.
    * Conditions (Applies To): Requires Accessibility Service permission. Lifecycle managed by `MainViewModel`.
* **Interface: IBuildService (AIDL)**
    * Description (Looks Like): An Interface Definition Language file.
    * Description (Does): Defines the remote methods (`startBuild`, `updateNotification`) the `MainViewModel` uses to control the `BuildService`.
    * Conditions (Applies To): IPC mechanism.
* **Interface: IBuildCallback (AIDL)**
    * Description (Looks Like): An Interface Definition Language file.
    * Description (Does): Defines the remote methods (`onLog`, `onSuccess`, `onFailure`) the `BuildService` uses to communicate back to the `MainViewModel`.
    * Conditions (Applies To): IPC mechanism.

### C. Build Pipeline Logic

* **Class: BuildOrchestrator**
    * Description (Looks Like): A Kotlin class containing a list of `BuildStep` objects.
    * Description (Does): Takes a list of `BuildStep` objects and executes them sequentially, reporting progress via the callback and halting execution on the first failure.
    * Conditions (Applies To): Executed by `BuildService.startBuild`.
* **Class: DependencyResolver**
    * Description (Looks Like): A build logic class.
    * Description (Does): Reads project dependencies, resolves them, and sets up the necessary classpath for compilation.
    * Conditions (Applies To): Used as the first executable step in the build pipeline.
* **Class: GenerateSourceMap**
    * Description (Looks Like): A build logic class.
    * Description (Does): Creates a file linking compiled application resources (e.g., UI elements) to their source code file and line number for future contextual lookups.
    * Conditions (Applies To): Used by `BuildService` before resource compilation.
* **Class: KotlincCompile**
    * Description (Looks Like): A build logic class.
    * Description (Does): Invokes the native Kotlin compiler (`kotlinc`) to convert Kotlin source files (`.kt`) into Java bytecode (`.class`).
    * Conditions (Applies To): Requires `kotlinc` tool path, `android.jar` path, source directory, and classpath.
* **Build Steps (Aapt2Compile, Aapt2Link, D8Compile, ApkBuild, ApkSign)**
    * Description (Looks Like): Separate build logic classes.
    * Description (Does): Each class handles a specific native tool invocation: compiling resources, linking resources/manifest to an APK, converting bytecode to DEX, packaging, and signing the final APK.
    * Conditions (Applies To): Sequentially executed by `BuildOrchestrator` after `DependencyResolver`.

### D. API and Version Control

* **Class: JulesCliClient**
    * Description (Looks Like): A static Kotlin object for API calls.
    * Description (Does): Executes local Jules CLI commands (`createSession`, `listSources`, `pullPatch`) to manage AI sessions and retrieve patches.
    * Conditions (Applies To): Used by `MainViewModel` for all Jules-based AI tasks.
* **Class: GeminiApiClient**
    * Description (Looks Like): A static Kotlin object for API calls.
    * Description (Does): Direct HTTP client for calling the Gemini API to generate content for contextless prompts.
    * Conditions (Applies To): Used by `MainViewModel` when Gemini Flash is selected for a task.
* **Class: GitManager**
    * Description (Looks Like): A wrapper class for JGit functions.
    * Description (Does): Handles Git operations including repository `clone`, initializing a repository, and applying a `gitPatch` (received from Jules) to the local project files.
    * Conditions (Applies To): Used during project cloning and patch application.
* **Data Classes: Jules API Models**
    * Description (Looks Like): Classes like `Source`, `GitHubRepo`, `Session`, `Activity`, `Artifact`, `GitPatch`.
    * Description (Does): Data transfer objects (DTOs) defining the structure for serializing and deserializing JSON data exchanged with the Jules API.
    * Conditions (Applies To): Used by `JulesCliClient` and `MainViewModel`.

### E. Core Utilities

* **Class: ToolManager**
    * Description (Looks Like): A static Kotlin object.
    * Description (Does): Provides utility for locating the paths of native build tools (like `aapt2`) and required JAR files (`android.jar`) packaged within the application's assets or `jniLibs`.
    * Conditions (Applies To): Called during `BuildService` initialization.
* **Class: ApkInstaller**
    * Description (Looks Like): A static Kotlin object.
    * Description (Does): Uses the Android `PackageInstaller` service to silently install the newly built APK file on the device.
    * Conditions (Applies To): Executed after a successful build by `BuildService`.
* **Class: ProcessExecutor**
    * Description (Looks Like): A static Kotlin object.
    * Description (Does): Executes external command-line commands (like `kotlinc` or `jules`) and synchronously captures the process's exit code and combined output.
    * Conditions (Applies To): Used by all build steps and CLI clients.
* **Class: SourceMapParser**
    * Description (Looks Like): A utility class.
    * Description (Does): Parses the source map file (generated by `GenerateSourceMap`) into an in-memory structure (`sourceMap`) for fast lookup of code location based on an inspected UI resource ID.
    * Conditions (Applies To): Called after a successful build in `MainViewModel`.

11/19/2025 10:21 pm