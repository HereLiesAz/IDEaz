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

## V. SettingsScreen (com.hereliesaz.ideaz.ui.SettingsScreen)

* **Scrollable Column with Haze Effect**
    * Description (Looks Like): Standard vertical scrolling settings list.
* **API Keys Section**
    * **GitHub Personal Access Token**: Input for the GitHub PAT used for repo management and automated issue reporting.
    * **Jules / AI Studio Keys**: Inputs for other AI providers.
* **Permissions Section**
    * ... (Overlay, Accessibility, Notification, Install, Screen Capture checks)
* **Preferences Section**
    * **Show Cancel Warning Checkbox**: Toggles cancellation dialog.
    * **Auto-report IDE internal errors Checkbox**: Toggles the automated GitHub issue reporting feature (`GithubIssueReporter`).
* **Theme Dropdown**
    * ... (Auto, Dark, Light, System)
* **Log Level Dropdown**
    * ... (Info, Debug, Verbose)
* **"Clear Build Caches" Button**
    * ...

---

## VII. Invisible Backend Infrastructure

### A. ViewModels and State Management

* **Class: MainViewModel (AndroidViewModel)**
    * Description (Does): Centralizes application logic. **New:** Implements `handleIdeError` to route internal crashes to the `GithubIssueReporter` (via API) while routing build failures to the AI Debugger.
* **Class: SettingsViewModel**
    * Description (Does): Manages settings, including the new `KEY_AUTO_REPORT_BUGS` and `KEY_GITHUB_TOKEN`.

### B. Services and Inter-Process Communication (IPC)

* **Class: BuildService (Service)**
    * Description (Does): Executes the build pipeline. **Updated:** Runs `GenerateSourceMap` as the final step after signing.
* **Class: UIInspectionService (AccessibilityService)**
    * ... (Unchanged)

### E. Core Utilities

* **Class: ToolManager**
    * Description (Does): Locates native tools. **Updated:** Validates asset files (`android.jar`) on load and repairs them if they are 0-byte/corrupt.
* **Class: GithubIssueReporter**
    * Description (Does): **New utility.** Takes a `Throwable` and `contextMessage`, creates a formatted markdown bug report, and posts it to the `HereLiesAz/IDEaz` GitHub repo via API. Falls back to a browser intent if the API fails.
* **Class: MainActivity**
    * Description (Does): **Updated:** Registers a `packageInstallReceiver` to detect when the user's app is installed/updated and launches it immediately.