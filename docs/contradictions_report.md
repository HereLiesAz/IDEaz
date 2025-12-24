# Documentation vs. Codebase Contradictions Report

This report documents discrepancies identified between the project's documentation and the actual source code state as of the current audit.

## 1. File Structure Discrepancies (`docs/file_descriptions.md`)

*   **`JulesApiClient` Location:**
    *   **Docs:** Lists `app/src/main/kotlin/com/hereliesaz/ideaz/api/JulesApiClient.kt`.
    *   **Code:** Actual location is `app/src/main/kotlin/com/hereliesaz/ideaz/jules/JulesApiClient.kt`.
*   **Missing Build Logic:**
    *   **Docs:** Missing references to new build steps in `buildlogic/`: `PythonInjector.kt`, `ScalaCompile.kt`, `SmaliCompile.kt`, `RemoteBuildManager.kt`, `JavaCompile.kt`.
*   **Missing UI Screens:**
    *   **Docs:** Missing `LibrariesScreen.kt` (Dependency Manager) and `FileExplorerScreen.kt`.
    *   **Code:** These screens exist and are reachable via `IdeNavRail`.
*   **Missing Utils:**
    *   **Docs:** Missing numerous utility classes in `utils/`: `BackupManager.kt`, `DependencyManager.kt`, `EnvironmentSetup.kt`, `ProjectConfigManager.kt`, etc.
*   **Assets:**
    *   **Docs:** Claims workflows reside in `app/src/main/assets/workflows/`.
    *   **Code:** This directory does not exist. Workflows are hardcoded as strings in `ProjectConfigManager.kt`.

## 2. Core Philosophy Discrepancies (`docs/blueprint.md`)

*   **"Post-Code" vs. File Explorer:**
    *   **Docs:** Emphasize a "Post-Code" or "No-Code" environment where users interact via the overlay.
    *   **Code:** Includes a functional `FileExplorerScreen` and `CodeEditor`, reachable via the "Files" tab. While potentially for debugging, its prominence contradicts the strict "Post-Code" messaging.

## 3. Workflow Configuration (`docs/workflow.md`, `docs/data_layer.md`)

*   **Merged Workflows:**
    *   **Docs:** `workflow.md` claims `build-and-release.yml` replaces separate `android_ci_jules.yml` and `release.yml`.
    *   **Code:** `ProjectConfigManager.kt` explicitly injects `android_ci_jules.yml` and `release.yml` separately.
*   **Asset Injection:**
    *   **Docs:** `data_layer.md` states workflows are injected from `assets/workflows`.
    *   **Code:** They are injected from hardcoded strings in `ProjectConfigManager.kt`.

## 4. UI/Screen Definitions (`docs/screens.md`)

*   **Missing Screens:**
    *   Does not document the **File Explorer** ("Files" tab) or **Dependency Manager** ("Libs" tab).

## 5. API/Package Discrepancies

*   **Jules API:** The documentation places `JulesApiClient` in the `api` package, but the code has moved it to a dedicated `jules` package, likely to separate the Agentic Interface from standard REST clients.

## 6. Hybrid Host / Zipline Status

*   **Zipline:**
    *   **Docs:** `TODO.md` marked Zipline integration as complete.
    *   **Code:** `MainViewModel.kt` explicitly disables Zipline loading due to API deprecation (`// FIXME: Zipline API loadOnce/load is deprecated...`).

## Remediation Plan

The documentation update plan includes:
1.  Updating `file_descriptions.md` to reflect the actual file tree.
2.  Updating `screens.md` to include the developer tools (Files, Libs) and clarify the Editor's role.
3.  Updating `data_layer.md` and `workflow.md` to correct the workflow injection mechanism and filenames.
4.  Updating `TODO.md` to reflect the blocked status of Zipline.
5.  Updating `misc.md` to include missing key libraries.
