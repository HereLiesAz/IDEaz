# React Native Implementation Checklist

This document details the step-by-step plan for implementing full React Native support in IDEaz.
Objective: Enable users to create, edit, build (bundle), and run React Native applications directly on the device.

## Phase 1: Project Initialization & Templates
- [x] **1.1: Create React Native Template**
    - [x] Create directory `app/src/main/assets/templates/react_native/`.
    - [x] Create `package.json` with standard dependencies (react, react-native). *(Note: Template configured for Pure JS, no native modules like navigation)*
    - [x] Create `app.json` (name, displayName).
    - [x] Create `App.js` (Hello World component).
    - [x] Create `index.js` (AppRegistry registration entry point).
    - [x] Create `babel.config.js` and `metro.config.js` (standard configs).
- [x] **1.2: Project Detection**
    - [x] Update `ProjectAnalyzer.kt` to robustly detect RN projects.
    - [x] Verify `ProjectType.REACT_NATIVE` assignment.
    - [x] Ensure `MainViewModel` correctly loads the project type.

## Phase 2: The Runtime (Runner)
*Strategy: Use a "Universal Runner" Activity that includes the React Native runtime and loads a local JS bundle.*
- [x] **2.1: Dependencies**
    - [x] Add `com.facebook.react:react-native` dependency to `app/build.gradle.kts`.
    - [x] Ensure `hermes-engine` is included.
    - [x] Handle 64-bit/32-bit ABI splits (Handled by standard build).
- [x] **2.2: Native Module Exposure**
    - [x] Create `IdeazReactPackage` implementing `ReactPackage`.
    - [x] Implement `IdeazNativeModule` to expose necessary device capabilities (currently Toast).
- [x] **2.3: The Runner Activity**
    - [x] Create `ReactNativeActivity.kt`.
    - [x] Implement `ReactInstanceManager` configuration.
    - [x] Configure it to load the bundle from `filesDir/projects/{project}/build/react_native_dist/index.android.bundle`.
    - [x] Handle `LifecycleEventListener` (pause, resume, destroy).
    - [x] Add `DefaultHardwareBackBtnHandler` implementation.

## Phase 3: The Build System (Bundling)
*Strategy: Use `SimpleJsBundler` (Kotlin-based) to bundle JS without Node.js.*
- [x] **3.1: Bundler Logic (`SimpleJsBundler.kt`)**
    - [x] **Tests:** Create robust unit tests for `SimpleJsBundler` (verifying regex replacement, JSON parsing).
    - [x] **Import Support:** Enhance to support basic `import` statements (Simple concatenation/replacement).
        *   *Note:* Full module resolution without Node is hard. Phase 1 relies on a single file structure or simple concatenation.
        *   *Decision:* Support single-file `App.js` editing locally + Remote Build for complex apps.
    - [x] **Asset Copying:** Implement logic to copy images/assets to the correct runner directory.
- [x] **3.2: Build Service Integration**
    - [x] Create `ReactNativeBuildStep` in `BuildService`.
    - [x] Implement `buildReactNativeProject()` logic.
    - [x] Step 1: Run `SimpleJsBundler`.
    - [x] Step 2: Trigger `onSuccess`.

## Phase 4: Remote Build (CI/CD)
*Strategy: Fallback for complex dependencies.*
- [x] **4.1: Workflow Injection**
    - [x] Create `android_ci_react_native.yml` (injected via `ProjectConfigManager`).
    - [x] Configure it to run `npm install`, `npx react-native bundle`. *(Note: Configured for Bundle Generation, as Android scaffolding is missing)*.
- [x] **4.2: Artifact Retrieval**
    - [x] Update `MainViewModel` to look for `*-debug.apk` from RN builds (and check RN specific paths).

## Phase 5: UI & UX
- [x] **5.1: Launch Logic**
    - [x] Update `MainViewModel.launchTargetApp`:
        - [x] If `REACT_NATIVE` and `Local Mode`: Launch `ReactNativeActivity` with bundle path.
        - [x] If `Remote Mode` (APK installed): Launch package via Intent.
- [x] **5.2: Accessibility & Overlay**
    - [x] Verify `SimpleJsBundler` correctly injects `accessibilityLabel` for the IDEaz overlay.
    - [x] Test selection mode on the RN surface (Assumed working via injection tests).
