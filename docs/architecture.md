# Architecture Overview

## The Delegate Pattern
To prevent `MainViewModel` from becoming a "God Object," logic is separated into **Delegates**.
* **Delegates** hold their own logic and private state.
* **MainViewModel** holds the `StateDelegate` (shared state) and instantiates the functional delegates.
* **UI** observes flows exposed by `MainViewModel`, which are just proxies to the Delegates' flows.

## Unified Overlay Architecture
The application now uses `IdeazOverlayService` as the primary UI container for the entire user experience ("System Alert Window throughout the entire UX").
*   **IdeazOverlayService**: Hosts the `IdeNavHost` (Settings, Project Screens) and the `IdeNavRail`.
*   **Window Management**: Dynamically switches between `MATCH_PARENT` (Settings/Selection) and `WRAP_CONTENT` (Interact/Docked) to manage touch interception.
*   **MainActivity**: Acts solely as a permission launcher and entry point, delegating UI to the Service.

## The Overlay Loop (Legacy "Split Brain" Fix - Now Unified)
The Overlay system manages state between the ViewModel and the Window.

1.  **Trigger:** User clicks "Select" in `IdeNavRail`.
2.  **Action:** `MainViewModel.toggleSelectMode(true)` is called.
3.  **Service:** `IdeazOverlayService` observes state and updates `layoutParams` to `MATCH_PARENT` with focusable flags.
4.  **Interaction:** User drags a rectangle on the `SelectionOverlay`.
5.  **Response:** `MainViewModel` receives the selection via `OverlayDelegate` and opens `ContextualChatOverlay`.

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
1.  **Architecture:** The Web Runtime is embedded directly into `IdeazOverlayService` as a `WebProjectHost` composable (Layer 0).
2.  **State Management:** `MainViewModel` manages `currentWebUrl`. When set, the UI switches to "Run Mode" (Web View + IDE Controls), rendering the WebView in the Overlay window (which expands to full screen).
3.  **Logging:** `WebProjectHost` uses `WebChromeClient` to capture console logs and errors, broadcasting them to the IDE's global console via `AI_LOG`.
4.  **Git Sync:** Web builds automatically trigger a `git push` via `BuildDelegate` to ensure remote CI/Deployment synchronization.

## JNA & Native Tools
* **JNA Crash:** To support `LazySodium` (Secrets), we force `System.setProperty("jna.nosys", "true")` in `MainApplication`. This prevents the app from loading the incompatible system-provided `libjnidispatch.so`.
* **Toolchain:** `ToolManager` is designed to run in a "hostile" Android environment. It looks for binaries in `local_build_tools` (downloaded) but falls back to extracting `lib<tool>.so` from the APK's native lib dir if needed.
