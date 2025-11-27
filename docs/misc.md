# Miscellaneous Notes

## Environment Setup
*   **Host Machine (Agent):**
    *   Requires Java 17+.
    *   Requires Android SDK (Command Line Tools, Platform Tools, Build Tools).
    *   Use `./setup_env.sh` to configure the environment.
*   **Target Device (App Runtime):**
    *   The app installs its own tools (`aapt2`, `java`, `d8`) into `filesDir/tools`.
    *   These are extracted from the APK assets or `nativeLibraryDir`.

## Build Tools
*   **`aapt2`:** Used for resource compilation.
*   **`kotlinc`:** Used for Kotlin compilation. Note: The app uses `kotlinc-android` artifact.
*   **`d8`:** Used for converting `.class` files to `.dex`.
*   **`apksigner`:** Used for signing the final APK.

## External Libraries
*   **JGit:** Used for Git operations.
*   **Rosemoe Editor:** Used for the code editor UI.
*   **Composables Core:** Used for the Bottom Sheet.
*   **Retrofit:** Used for API calls.

## Known Issues
*   **Timeout:** Large builds might time out. The `BuildService` has a supervisor job but can still be killed by the OS if memory is low.
*   **Caching:** `BuildCacheManager` is basic. Sometimes a "Clean Build" (deleting `build/`) is required.
*   **Symlinks:** `Files.createSymbolicLink` requires Android O (API 26).

## Tips
*   **Logs:** The app logs extensively to `Logcat`. Use `adb logcat -s IDEaz BuildService UIInspectionService` to debug.
*   **Screenshots:** The `ScreenshotService` or `UIInspectionService` handles screen capture.
