# Architecture Overview

## The Delegate Pattern
To prevent `MainViewModel` from becoming a "God Object," logic is separated into **Delegates**.
* **Delegates** hold their own logic and private state.
* **MainViewModel** holds the `StateDelegate` (shared state) and instantiates the functional delegates.
* **UI** observes flows exposed by `MainViewModel`, which are just proxies to the Delegates' flows.

## The Overlay Loop (The "Split Brain" Fix)
The Overlay system has been refactored to eliminate state desynchronization between the Activity and the Service.

1.  **Trigger:** User clicks "Select" in `IdeNavRail`.
2.  **Action:** `MainViewModel.toggleSelectMode(true)` is called.
3.  **Delegate:** `OverlayDelegate` broadcasts `com.hereliesaz.ideaz.TOGGLE_SELECT_MODE` with `ENABLE=true`.
4.  **Service:** `UIInspectionService` receives the broadcast and updates its **local** state to draw the `OverlayCanvas` with `FLAG_NOT_FOCUSABLE` (intercept touches).
5.  **Interaction:** User drags a rectangle.
6.  **Report:** `UIInspectionService` broadcasts `com.hereliesaz.ideaz.SELECTION_MADE` with the `Rect`.
7.  **Response:** `MainViewModel` receives the broadcast via `SystemEventDelegate`, captures a screenshot, and opens the `ContextualChatOverlay`.

## The "Race to Build"
1.  **Init:** User clicks "Save & Initialize" in `ProjectSetupTab`.
2.  **Injection:** `RepoDelegate.forceUpdateInitFiles()` writes `android_ci_jules.yml` and `setup_env.sh` to the local disk.
3.  **Push:** `GitDelegate` commits and pushes these files to GitHub immediately.
4.  **Race:**
    * **Local:** `BuildDelegate` starts `BuildService` locally.
    * **Remote:** GitHub Actions triggers off the push and starts the cloud build.
    * **Winner:** Whichever finishes first notifies the user (Local via callback, Remote via future webhook/polling).

## Web Runtime Architecture
For `ProjectType.WEB`:
1.  **Architecture:** The Web Runtime is embedded directly into `MainScreen` as a `WebProjectHost` composable (Layer 0), rather than launching a separate Activity.
2.  **State Management:** `MainViewModel` manages `currentWebUrl`. When set, the UI switches to "Run Mode" (Web View + IDE Controls), bypassing the System Overlay service used for Android apps.
3.  **Logging:** `WebProjectHost` uses `WebChromeClient` to capture console logs and errors, broadcasting them to the IDE's global console via `AI_LOG`.
4.  **Git Sync:** Web builds automatically trigger a `git push` via `BuildDelegate` to ensure remote CI/Deployment synchronization.

## JNA & Native Tools
* **JNA Crash:** To support `LazySodium` (Secrets), we force `System.setProperty("jna.nosys", "true")` in `MainApplication`. This prevents the app from loading the incompatible system-provided `libjnidispatch.so`.
* **Toolchain:** `ToolManager` is designed to run in a "hostile" Android environment. It looks for binaries in `local_build_tools` (downloaded) but falls back to extracting `lib<tool>.so` from the APK's native lib dir if needed.