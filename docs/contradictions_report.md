# Documentation Contradictions & Discrepancies Report

This report highlights glaring contradictions between the project documentation and the actual source code, identified during a comprehensive audit and subsequently resolved.

## Resolved Contradictions

### 1. Service Architecture & Naming
*   **Contradiction:** Documentation (`architecture.md`, `screens.md`, `file_descriptions.md`) frequently referenced a `UIInspectionService`, sometimes describing it as an Accessibility Service and sometimes as the Overlay Service.
*   **Reality:** The code has two distinct services:
    *   `IdeazAccessibilityService.kt` (Accessibility Service): Handles `AccessibilityNodeInfo` retrieval (Inspection).
    *   `IdeazOverlayService.kt` (Foreground Service): Handles the `OverlayView` (Visuals) via `TYPE_APPLICATION_OVERLAY`.
*   **Resolution:** Documentation has been updated to explicitly name and describe these two services and their distinct roles. `UIInspectionService` references have been removed.

### 2. Build Pipeline & Toolchain
*   **Contradiction:** `docs/build_pipeline.md` claimed that build tools (`aapt2`, `kotlinc`, `d8`) were static `aarch64` binaries bundled in `jniLibs` and executed natively.
*   **Reality:** `ToolManager.kt` implements a download-based strategy (`tools.zip` -> `filesDir/local_build_tools`). Most tools are JARs (`kotlin-compiler.jar`, `d8.jar`) executed via the bundled `java` binary. `jniLibs` is unused.
*   **Resolution:** `docs/build_pipeline.md` and `docs/file_descriptions.md` have been rewritten to reflect the downloadable JAR-based toolchain.

### 3. Project Screen Tabs
*   **Contradiction:** `docs/screens.md` listed tabs as "Create, Load, Clone" or "Load, Clone, Create". `AGENTS.md` noted the correct order.
*   **Reality:** `ProjectScreen.kt` defines the tabs as `Setup`, `Load`, `Clone`. "Create" is a mode within the "Setup" tab.
*   **Resolution:** `docs/screens.md` has been updated to match the code.

### 4. CI/CD Workflow Names
*   **Contradiction:** `docs/workflow.md` and `docs/testing.md` referred to `android_ci_jules.yml` and `release.yml`.
*   **Reality:** The actual workflow file is `build-and-release.yml`, which handles both CI and Release logic.
*   **Resolution:** All workflow documentation has been updated to reference `build-and-release.yml`.

### 5. Missing Feature Documentation
*   **Contradiction:** `docs/build_pipeline.md` did not mention the Hybrid Host (Redwood/Zipline) build steps, despite Phase 11 being marked complete.
*   **Reality:** `BuildService.kt` includes steps for `RedwoodCodegen`, `ZiplineCompile`, and `ZiplineManifestStep`.
*   **Resolution:** Added "Hybrid Host Generation" steps to `docs/build_pipeline.md`.

## Remaining / Minor Notes
*   **JulesCliClient:** Exists in code but is marked as Legacy/Reference. Documentation now reflects this.
*   **React Native:** Support is now fully implemented (Native Runner + Bundler).
