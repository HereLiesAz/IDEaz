# Flutter Implementation Checklist

This document details the step-by-step plan for implementing full Flutter support in IDEaz.
Objective: Enable users to create, edit, build, and run Flutter applications.
Constraint: Local compilation of Dart is currently not possible due to missing binaries/environment. Primary strategy is Remote Build.

## Phase 1: Project Initialization & Templates
- [ ] **1.1: Create Flutter Template**
    - [ ] Create directory `app/src/main/assets/templates/flutter/`.
    - [ ] Create `pubspec.yaml` (name, sdk version, dependencies).
    - [ ] Create `lib/main.dart` (Counter App or Hello World).
    - [ ] Create `android/` directory structure (settings.gradle, build.gradle, AndroidManifest.xml) - *Required for CI build*.
    - [ ] Create `analysis_options.yaml` (linting).
- [ ] **1.2: Project Detection**
    - [ ] Update `ProjectAnalyzer.kt` to detect `pubspec.yaml`.
    - [ ] Verify `ProjectType.FLUTTER` assignment.

## Phase 2: Build System (Remote Strategy)
*Since we cannot run `flutter build apk` locally.*
- [ ] **2.1: CI Workflow Injection**
    - [ ] Create `android_ci_flutter.yml` in assets.
    - [ ] Define steps:
        - [ ] Setup Java & Flutter (`subosito/flutter-action`).
        - [ ] `flutter pub get`.
        - [ ] `flutter build apk --debug`.
        - [ ] Rename artifact to `IDEaz-{version}-debug.apk` (or similar pattern).
        - [ ] Upload Artifact.
- [ ] **2.2: Git Integration**
    - [ ] Ensure `GitDelegate` forces a push when "Build" is requested for Flutter projects.
    - [ ] Add explicit "Push & Build" button in UI for Flutter projects (replacing local "Build" button).

## Phase 3: Artifact Management & Installation
- [ ] **3.1: Artifact Polling**
    - [ ] Ensure `MainViewModel` polling logic picks up the Flutter APK.
    - [ ] Handle `app-release.apk` vs `app-debug.apk` naming from standard Flutter builds.
- [ ] **3.2: Installation**
    - [ ] `BuildService` handles download and install via `PackageInstaller` (already supported).

## Phase 4: Editor Features (Local)
- [ ] **4.1: Syntax Highlighting**
    - [ ] Update `EnhancedCodeEditor` (if used) or log viewer to support `.dart` syntax (basic keywords).
- [ ] **4.2: Dependency Management (Visual)**
    - [ ] Parse `pubspec.yaml` manually in Kotlin.
    - [ ] Display dependencies in the "Dependencies" screen.
    - [ ] Allow adding dependencies (modifying `pubspec.yaml` text).

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
- [ ] **6.1: Overlay Support**
    - [ ] Flutter renders to a generic SurfaceView/TextureView.
    - [ ] Accessibility Node mapping might be limited to what Flutter exposes to Accessibility Services.
    - [ ] Verify `IdeazAccessibilityService` can see Flutter widgets (Semantic Nodes).
