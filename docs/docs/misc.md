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
