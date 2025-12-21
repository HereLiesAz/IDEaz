# Flutter Implementation Checklist

This document details the step-by-step plan for implementing full Flutter support in IDEaz.
Objective: Enable users to create, edit, build, and run Flutter applications.
Constraint: Local compilation of Dart is currently not possible due to missing binaries/environment. Primary strategy is Remote Build.

## Phase 1: Project Initialization & Templates
- [x] **1.1: Create Flutter Template**
    - [x] Create directory `app/src/main/assets/templates/flutter/`.
    - [x] Create `pubspec.yaml` (name, sdk version, dependencies).
    - [x] Create `lib/main.dart` (Counter App or Hello World).
    - [x] Create `android/` directory structure (settings.gradle, build.gradle, AndroidManifest.xml) - *Required for CI build*.
    - [x] Create `analysis_options.yaml` (linting).
- [x] **1.2: Project Detection**
    - [x] Update `ProjectAnalyzer.kt` to detect `pubspec.yaml`.
    - [x] Verify `ProjectType.FLUTTER` assignment.

## Phase 2: Build System (Remote Strategy)
*Since we cannot run `flutter build apk` locally.*
- [x] **2.1: CI Workflow Injection**
    - [x] Create `android_ci_flutter.yml` in assets.
    - [x] Define steps:
        - [x] Setup Java & Flutter (`subosito/flutter-action`).
        - [x] `flutter pub get`.
        - [x] `flutter build apk --debug`.
        - [x] Rename artifact to `IDEaz-{version}-debug.apk` (or similar pattern).
        - [x] Upload Artifact.
- [x] **2.2: Git Integration**
    - [x] Ensure `GitDelegate` forces a push when "Build" is requested for Flutter projects.
    - [x] Add explicit "Push & Build" button in UI for Flutter projects (replacing local "Build" button).

## Phase 3: Artifact Management & Installation
- [x] **3.1: Artifact Polling**
    - [x] Ensure `MainViewModel` polling logic picks up the Flutter APK.
    - [x] Handle `app-release.apk` vs `app-debug.apk` naming from standard Flutter builds.
- [x] **3.2: Installation**
    - [x] `BuildService` handles download and install via `PackageInstaller` (already supported).

## Phase 4: Editor Features (Local)
- [x] **4.1: Syntax Highlighting**
    - [x] Update `EnhancedCodeEditor` (if used) or log viewer to support `.dart` syntax (basic keywords).
- [x] **4.2: Dependency Management (Visual)**
    - [x] Parse `pubspec.yaml` manually in Kotlin.
    - [x] Display dependencies in the "Dependencies" screen.
    - [x] Allow adding dependencies (modifying `pubspec.yaml` text).

## Phase 5: Investigation (Local Execution)
*Research tasks for potential future on-device support.*
- [ ] **5.1: Dart on Android**
    - [ ] Investigate running a pre-compiled `dart` binary on Android (termux-packages?).
    - [ ] If `dart` binary runs, we can use `dart run` or `flutter_tools` snapshot.
- [ ] **5.2: Flutter Engine Runner**
    - [ ] Investigate a "Universal Flutter Runner" similar to React Native.
    - [ ] Challenge: Flutter code is AOT compiled (Release) or requires JIT kernel (Debug).
    - [ ] Can we generate a kernel snapshot remotely and download *just* the snapshot to run on a generic local engine? (Hot Update).

## Phase 6: UI & Overlay Integration
- [x] **6.1: Overlay Support**
    - [x] Flutter renders to a generic SurfaceView/TextureView.
    - [x] Accessibility Node mapping might be limited to what Flutter exposes to Accessibility Services.
    - [x] Verify `IdeazAccessibilityService` can see Flutter widgets (Semantic Nodes). (Implemented Node Traversal Logging for verification).
