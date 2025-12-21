# Miscellaneous Documentation

## 1. Project Templates
IDEaz includes templates in `assets/templates/` for:
*   **Android:** Basic "Hello World" with Gradle wrapper.
*   **Web:** `index.html`, `style.css`, `script.js`.
*   **React Native:** Minimal `App.js` and `app.json`.
*   **Flutter:** (Placeholder) Basic structure.

## 2. The "Jules-CLI" (Deprecated)
The project contains code for `JulesCliClient` which wraps a binary. This is currently **unreliable** on many Android devices due to `seccomp` filters and binary compatibility. The `JulesApiClient` (Retrofit) is the preferred method.

## 3. Logs
*   **Infrastructure Logs:** `[IDE]` tag.
*   **Build Logs:** `[BUILD]` tag.
*   **AI Logs:** `[AI]` tag.
*   **Git Logs:** `[GIT]` tag.

## 4. Updates
The app checks `https://api.github.com/repos/HereLiesAz/IDEaz/releases` for updates.
*   **Upgrade:** Remote version > Local version.
*   **Downgrade:** Remote version < Local version.
*   **Reinstall:** Versions match but hashes differ.


<!-- Merged Content from docs/docs/misc.md -->

# Miscellaneous Notes

## Environment Setup
*   **Host Machine (Agent):**
    *   Requires Java 17+.
    *   Requires Android SDK (Command Line Tools, Platform Tools, Build Tools).
    *   Use `./setup_env.sh` to configure the environment.
*   **Target Device (App Runtime):**
    *   The app installs its own tools (`aapt2`, `java`, `d8`) into `filesDir/tools` or uses them directly from `nativeLibraryDir`.
    *   These are extracted/accessed by `ToolManager`.

## Build Tools
*   **`aapt2`:** Used for resource compilation.
*   **`kotlinc`:** Used for Kotlin compilation. Note: The app uses `kotlinc-android` artifact.
*   **`d8`:** Used for converting `.class` files to `.dex`.
*   **`apksigner`:** Used for signing the final APK.

## External Libraries
*   **JGit:** Used for Git operations.
*   **Retrofit:** Used for API calls.
*   **Composables Core:** Used for the Bottom Sheet implementation.
*   **AzNavRail:** Used for the Navigation Rail and Overlay Service logic.
*   **Sora Editor:** Used for the code editor UI components.

## Constraints & Known Issues
*   **Transparency:** The IDE overlay background MUST be transparent during "Interact/Select" modes, but **Settings and Setup screens must be Opaque**.
*   **Timeout:** Large builds might time out. The `BuildService` has a supervisor job but can still be killed by the OS if memory is low.
*   **Caching:** `BuildCacheManager` is basic. Sometimes a "Clear Cache" (deleting `build/`) is required.
*   **Symlinks:** `Files.createSymbolicLink` requires Android O (API 26).

## Tips
*   **Logs:** The app logs extensively to `Logcat`. Use `adb logcat -s IDEaz BuildService UIInspectionService` to debug.
*   **Screenshots:** The `ScreenshotService` or `OverlayDelegate` handles screen capture.
