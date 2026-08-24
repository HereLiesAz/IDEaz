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
* **Saved Settings and Credentials Section**
    * **Save Settings / Load Settings buttons**: export/import all settings (including every secure credential) as a password-protected encrypted file via `SecurityUtils`.
* **Signing Configuration Section**
    * **Select Custom Keystore**: SAF picker; imports a `.keystore`/`.jks` file to `filesDir/user_release.keystore`.
    * **Keystore Password / Key Alias / Key Password** fields, a **Save** button, and **Reset to Default** (reverts to the debug keystore).
* **API Keys Section**
    * **Jules API Key**, **GitHub Personal Access Token**, **AI Studio API Key** (Gemini), **Google Cloud Project Number** — each with a "Get Key" link to the provider's key-issuance page. Saving the GitHub token also refreshes the Clone tab's repo list (`fetchGitHubRepos()`).
    * **Free Providers** (Groq, Cerebras, Hugging Face, Mistral) and **Paid Providers** (OpenAI, Anthropic, DeepSeek) — one row each (`FreeProviderKeyRow`), same shape as the keys above: masked input, "Get Key" link, per-row Save.
    * **Gemini App (Accessibility)**: status button showing whether the Gemini-app accessibility bridge is granted; tapping it when ungranted opens Accessibility settings.
    * Every save/failure Toast in this section reports the actual underlying error (`SettingsViewModel.lastCredentialError`) on failure — see [`auth.md`](auth.md) §2 for why that matters on devices with no adb access.
* **AI Assignments Section**
    * One dropdown per task (Default, Project Initialization, Contextless Chat, Overlay Chat) listing every `AiModel` in `AiModels.availableModels`. An unset "Default" resolves automatically to the highest-ranked provider the user has a key for (`auth.md` §2.1), not a hardcoded model.
* **On-device Models Section** (`OnDeviceModelsSection`)
    * Lists locally-downloadable models, gated on AICore support / RAM-ABI requirements / a saved Hugging Face token where the model requires one.
* **Permissions Section**
    * An **"Open App Info"** button and explanatory text at the top: Android can label Accessibility/Overlay grants "restricted" for a sideloaded app until the user visits system App Info and enables them via its overflow menu — this jumps straight there.
    * Overlay, Accessibility, and Post Notifications checks, each with a one-line description of why IDEaz needs it. The Screen Capture (MediaProjection) row and Install Unknown Apps check were removed — in-app updates and screen capture were dormant/removed during permission minimization. Broad storage permissions were removed entirely (P0.2 permission-minimization); SAF pickers cover file access instead.
* **Preferences Section**
    * **Show Cancel Warning Checkbox**: Toggles cancellation dialog.
    * **Auto-report IDE internal errors Checkbox**: Toggles the automated GitHub issue reporting feature (`GithubIssueReporter`).
    * **Auto-debug build failures with Jules Checkbox**.
    * **Report IDE errors to HereLiesAz/IDEaz Checkbox**.
* **Theme Dropdown**
    * ... (Auto, Dark, Light, System)
* **Log Level Dropdown**
    * ... (Info, Debug, Verbose)
* **Updates Section**
    * **Check for Experimental Updates** button; an `AlertDialog` shows update progress/prompts to install when one is found.

---

## VII. Invisible Backend Infrastructure

### A. ViewModels and State Management

* **Class: MainViewModel (AndroidViewModel)**
    * Description (Does): Centralizes application logic. Uses Delegates (`AIDelegate`, `BuildDelegate`, etc.) to handle specific domains.
    * **Implements:** `handleIdeError` to route internal crashes to the `GithubIssueReporter` (via API) while routing build failures to the AI Debugger.
* **Class: SettingsViewModel**
    * Description (Does): Manages settings, secure-credential storage (`SECURE_CREDENTIAL_KEYS`, `AndroidKeystoreCredentialStore`), the ranked default-AI-model resolution (`AiModels.defaultRanking`), and `lastCredentialError` for surfacing real save/read failures in the UI (see [`auth.md`](auth.md)).

### B. Services and Inter-Process Communication (IPC)

* **Class: BuildService (Service)**
    * **Type:** `android:exported="false"`, `android:foregroundServiceType="dataSync"`. Runs in the main app process — no `android:process` attribute (a prior version of this doc claimed `:build_process`, which was never in the manifest).
    * Description (Does): Dispatches and polls a remote GitHub Actions build via `RemoteBuildManager`, then sideloads the resulting APK. (The on-device build toolchain was removed in Phase 0.)
* **Class: IdeazOverlayService (Service)**
    * **Type:** `android:exported="false"`, `android:foregroundServiceType="specialUse"` (with a `FOREGROUND_SERVICE_TYPE_SPECIAL_USE_DESCRIPTION` property, not a `permission` attribute — a prior version of this doc claimed one).
    * **Permissions:** `SYSTEM_ALERT_WINDOW`.
    * Description (Does): Hosts the main UI overlay (`OverlayView`) as a system alert window.
* **Class: ScreenshotService (Service)**
    * **Type:** `android:exported="false"`, `android:foregroundServiceType="mediaProjection"`.
    * Description (Does): Android-target screen capture (Phase 2), started only when `OverlayDelegate.isScreenCaptureEnabled()` - never reached on web/PWA projects.
* **Class: IdeazAccessibilityService (AccessibilityService)**
    * **Type:** `android:exported="true"`.
    * **Permissions:** `BIND_ACCESSIBILITY_SERVICE` (system-only — an app cannot bind to another app's accessibility service without holding this itself, so `exported="true"` here does not open the service to arbitrary callers).
    * Description (Does): Retrieves Node Info for inspection (Select-mode tap-to-inspect).
* **Class: GeminiAppBridgeAccessibilityService (AccessibilityService)**
    * **Type:** `android:exported="true"`, same `BIND_ACCESSIBILITY_SERVICE` gating as above.
    * Description (Does): Drives the Gemini App Bridge - relays prompts/responses through the Gemini app's own UI via accessibility node interaction, for the "Gemini App (Accessibility)" AI provider option.
* **Class: CrashReportingService (Service)**
    * **Type:** `android:exported="false"`, `android:process=":crash_reporter"`.
    * Description (Does): Handles fatal/non-fatal error reporting in an isolated process so it survives a main-process crash.

### E. Core Utilities

* **Class: GithubIssueReporter**
    * Description (Does): Utilities to post GitHub issues. Takes a `Throwable` and `contextMessage`, creates a formatted markdown bug report, and posts it to the `HereLiesAz/IDEaz` GitHub repo via API. Falls back to a browser intent if the API fails.
* **Class: MainActivity**
    * Description (Does): Registers a `packageInstallReceiver` to detect when the user's app is installed/updated and launches it immediately.
