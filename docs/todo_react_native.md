# React Native Implementation Checklist

This document details the step-by-step plan for implementing full React Native support in IDEaz.
Objective: Enable users to create, edit, build (bundle), and run React Native applications directly on the device.

## Phase 1: Project Initialization & Templates
- [ ] **1.1: Create React Native Template**
    - [ ] Create directory `app/src/main/assets/templates/react_native/`.
    - [ ] Create `package.json` with standard dependencies (react, react-native).
    - [ ] Create `app.json` (name, displayName).
    - [ ] Create `App.js` (Hello World component).
    - [ ] Create `index.js` (AppRegistry registration entry point).
    - [ ] Create `babel.config.js` and `metro.config.js` (standard configs).
- [ ] **1.2: Project Detection**
    - [ ] Update `ProjectAnalyzer.kt` to robustly detect RN projects.
    - [ ] Verify `ProjectType.REACT_NATIVE` assignment.
    - [ ] Ensure `MainViewModel` correctly loads the project type.

## Phase 2: The Runtime (Runner)
*Strategy: Use a "Universal Runner" Activity that includes the React Native runtime and loads a local JS bundle.*
- [ ] **2.1: Dependencies**
    - [ ] Add `com.facebook.react:react-native` dependency to `app/build.gradle.kts` (or a dynamic feature module if size is a concern).
    - [ ] Ensure `hermes-engine` is included (or JSC).
    - [ ] Handle 64-bit/32-bit ABI splits if necessary.
- [ ] **2.2: Native Module Exposure**
    - [ ] Create `IdeazReactPackage` implementing `ReactPackage`.
    - [ ] Implement `IdeazNativeModule` to expose necessary device capabilities (if any) or bridge IDEaz specific events.
- [ ] **2.3: The Runner Activity**
    - [ ] Create `ReactNativeActivity.kt`.
    - [ ] Implement `ReactInstanceManager` configuration.
    - [ ] Configure it to load the bundle from `filesDir/projects/{project}/build/index.android.bundle`.
    - [ ] Handle `LifecycleEventListener` (pause, resume, destroy).
    - [ ] Add "Dev Menu" trigger (optional, for debugging).

## Phase 3: The Build System (Bundling)
*Strategy: Use `SimpleJsBundler` (Kotlin-based) to bundle JS without Node.js.*
- [ ] **3.1: Bundler Logic (`SimpleJsBundler.kt`)**
    - [ ] **Tests:** Create robust unit tests for `SimpleJsBundler` (verifying regex replacement, JSON parsing).
    - [ ] **Import Support:** Enhance to support basic `import` statements (merge files? or limited support).
        *   *Note:* Full module resolution without Node is hard. Phase 1 might rely on a single file or simple concatenation.
        *   *Alternative:* If full bundling is impossible, push to GitHub for Metro build (Remote Build).
        *   *Decision:* Support single-file `App.js` editing locally + Remote Build for complex apps.
    - [ ] **Asset Copying:** Implement logic to copy images/assets to the correct runner directory.
- [ ] **3.2: Build Service Integration**
    - [ ] Create `ReactNativeBuildStep` in `BuildService`.
    - [ ] Implement `buildReactNativeProject()` method.
    - [ ] Step 1: Run `SimpleJsBundler`.
    - [ ] Step 2: (Optional) Download pre-built native libs if not in the Runner.
    - [ ] Step 3: Trigger `onSuccess`.

## Phase 4: Remote Build (CI/CD)
*Strategy: Fallback for complex dependencies.*
- [ ] **4.1: Workflow Injection**
    - [ ] Create `android_ci_react_native.yml` in assets.
    - [ ] Configure it to run `npm install`, `npx react-native bundle`, and `./gradlew assembleDebug`.
- [ ] **4.2: Artifact Retrieval**
    - [ ] Update `MainViewModel` to look for `*-debug.apk` from RN builds.

## Phase 5: UI & UX
- [ ] **5.1: Launch Logic**
    - [ ] Update `MainViewModel.launchTargetApp`:
        - [ ] If `REACT_NATIVE` and `Local Mode`: Launch `ReactNativeActivity` with bundle path.
        - [ ] If `Remote Mode` (APK installed): Launch package via Intent.
- [ ] **5.2: Accessibility & Overlay**
    - [ ] Verify `SimpleJsBundler` correctly injects `accessibilityLabel` for the IDEaz overlay.
    - [ ] Test selection mode on the RN surface.
