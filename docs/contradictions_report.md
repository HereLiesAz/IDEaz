# Documentation Contradictions Report

This document tracks identified contradictions between the project documentation and the actual codebase implementation. Agents should refer to this report to understand current discrepancies and work towards resolving them.

## 1. Jules CLI Integration
*   **Documentation Claim:** `docs/file_descriptions.md` previously implied `JulesCliClient` might be active.
*   **Codebase Reality:** `JulesCliClient` is present but unused. The application relies entirely on `JulesApiClient` (Retrofit/HTTP) for AI interactions.
*   **Status:** Documentation updated to reflect `JulesCliClient` as deprecated/unused.

## 2. Zipline / Hot Reload
*   **Documentation Claim:** `docs/blueprint.md` previously implied Zipline features were active or "In Progress".
*   **Codebase Reality:** The build pipeline (`BuildService.kt`, `buildlogic/`) fully supports `RedwoodCodegen` and `ZiplineCompile`. However, the runtime loading logic in `MainViewModel.kt` (`reloadZipline`) is explicitly commented out and disabled due to Zipline API deprecation (`loadOnce`/`load` removed/changed).
*   **Status:** Documentation updated to mark Zipline Runtime as **DISABLED** / **BLOCKED**.

## 3. Flutter Support
*   **Documentation Claim:** Some docs implied native local build support or "Phase 1-6" completion.
*   **Codebase Reality:** Flutter support is implemented via **Remote Build** only. `BuildDelegate` triggers a push to GitHub, and `ProjectConfigManager` injects the relevant CI workflow. There is no local `flutter` toolchain on the device.
*   **Status:** Documentation updated to clarify "Supported via Remote Build".

## 4. File Explorer & Code Editor
*   **Documentation Claim:** `README.md` previously ambiguous about their role.
*   **Codebase Reality:** `FileExplorerScreen.kt` and `CodeEditor.kt` exist and are functional.
*   **Status:** Documentation clarified that these are "Escape Hatches" and not the primary "Post-Code" interface.

## 5. Python Support
*   **Documentation Claim:** `docs/Python on Android UI Research.md` existed, but implementation status was unclear.
*   **Codebase Reality:** `PythonInjector.kt` exists in `buildlogic/` and is actively used in `BuildService.kt` to inject Chaquopy runtime AARs if Python assets are detected.
*   **Status:** Confirmed as Active/Implemented in `file_descriptions.md`.
