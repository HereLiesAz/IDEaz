# React Native Implementation Plan

This document outlines the tasks required to implement React Native support in IDEaz.

## 1. Template Enhancement (Android Shell)
The current React Native template (`templates/react_native`) contains only JavaScript files. To run on Android, we need a native "shell" or "host" application.
*   **Task:** Create a minimal Android project structure in `templates/react_native/android/`.
*   **Contents:**
    *   `AndroidManifest.xml`: Declares `MainActivity`.
    *   `MainActivity.java`: Extends `ReactActivity`.
    *   `MainApplication.java`: Extends `Application` and implements `ReactApplication`.
    *   `build.gradle`: Declares dependencies on `react-android`.

## 2. Dependency Management
We need to resolve and include the React Native libraries in the build.
*   **Task:** Update `DependencyResolver` or the template's build configuration to include:
    *   `com.facebook.react:react-android`
    *   `com.facebook.react:hermes-android` (or `jsc-android`)
*   **Challenge:** Ensure all transitive dependencies (SoLoader, AndroidX, etc.) are resolved correctly by the on-device resolver.

## 3. Custom JS Bundler (The "Metro" Replacement)
We cannot run the standard Metro bundler (Node.js) on Android.
*   **Task:** Implement `SimpleJsBundler` (Kotlin) in `buildlogic`.
*   **Features:**
    *   **Single-File Support (MVP):** Read `App.js` and write it to `assets/index.android.bundle`.
    *   **Source Map Injection:** Use Regex to find JSX tags (e.g., `<View`) and inject a `accessibilityLabel` or `testID` attribute containing `__source:line_number__`.
    *   **Boilerplate Wrapping:** Wrap the code in the necessary `AppRegistry.registerComponent` calls if the user code doesn't include them explicitly (or ensure the template includes them).

## 4. Build Step Implementation (Pending)
*   **Status:** Not implemented. `SimpleJsBundler` exists but is not called by `BuildService`.
*   **Task:** Implement `ReactNativeBuildStep.kt` or integrate logic into `BuildService.kt`.
*   **Workflow:**
    1.  **Setup:** Copy the Android shell from the template to the build directory.
    2.  **Bundle:** Run `SimpleJsBundler` to generate `src/main/assets/index.android.bundle`.
    3.  **Compile:** Run `javac` / `kotlinc` to compile the `MainActivity` and `MainApplication`.
    4.  **Resource Compilation:** Run `aapt2` to compile resources.
    5.  **Dexing:** Run `d8` to convert the compiled classes and the React Native AARs into `classes.dex`.
    6.  **Packaging:** Bundle everything into an APK.

## 5. UI Inspection & Source Mapping
*   **Task:** Integrate with the "Post-Code" overlay.
*   **Logic:**
    *   When the user taps an element, `UIInspectionService` reads the `contentDescription` (mapped from `accessibilityLabel`).
    *   If the description matches the pattern `__source:line__`, extract the file and line.
    *   Pass this context to the AI Agent.
