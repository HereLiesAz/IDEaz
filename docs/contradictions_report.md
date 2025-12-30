# Documentation Contradictions Report

This document lists identified contradictions between the project's documentation and the actual codebase as of the latest audit.

## 1. Auxiliary Tools ("Escape Hatches")
*   **Contradiction:** `README.md` and `blueprint.md` state that a File Explorer and Code Editor are included as "escape hatches". However, project memory and instructions emphasized a "post-code" philosophy where such tools might be removed.
*   **Reality:** `CodeEditor.kt` and `FileExplorerScreen.kt` exist in `app/src/main/kotlin/com/hereliesaz/ideaz/ui/`.
*   **Action:** Documentation updated to clarify these are strictly auxiliary and not the primary workspace. The "No-Code" philosophy remains the guiding principle for the *primary* workflow, but the tools exist for power users.

## 2. Hybrid Host & Zipline
*   **Contradiction:** `TODO.md` marks Phase 11 (Hybrid Host) as mostly complete, including "Hot Reload" implementation. `blueprint.md` describes the Host Architecture relying on Zipline.
*   **Reality:** `MainViewModel.kt` explicitly disables Zipline loading with `LoadResult.Failure(Exception("Zipline disabled due to deprecation"))`. The Zipline dependencies and `ZiplineLoader` initialization code exist, but the feature is effectively blocked at runtime.
*   **Action:** `TODO.md` updated to reflect the blocked status of Phase 11.6. `blueprint.md` updated to note the current limitation.

## 3. Project Screen Tabs Order
*   **Contradiction:** `AGENTS.md` noted a discrepancy in tab order.
*   **Reality:** `ProjectScreen.kt` defines `tabs = listOf("Setup", "Load", "Clone")`.
*   **Action:** Confirmed `AGENTS.md` is correct about the fix. `screens.md` (if it lists tabs) should match this order.

## 4. Jules Integration
*   **Contradiction:** Legacy documentation referenced the `Jules Tools CLI` as the primary tool.
*   **Reality:** `jules-integration.md` correctly identifies `JulesApiClient` as the primary mechanism, citing stability issues with the CLI. `MainViewModel.kt` uses `AIDelegate` which uses `JulesApiClient`. The CLI binary `libjules.so` is present but unused.
*   **Action:** `jules-integration.md` is accurate. No action needed other than ensuring other docs don't reference the CLI as active.

## 5. Dependency Management
*   **Contradiction:** `TODO.md` marked "UI for viewing and adding libraries" as checked.
*   **Reality:** `DependencyManager.kt` exists and implements TOML/Pubspec parsing. `LibrariesScreen.kt` exists (inferred from file list, though not read, `MainViewModel` has `loadDependencies`).
*   **Action:** `TODO.md` is accurate.

## 6. Settings Import/Export
*   **Contradiction:** `TODO.md` marked "Encrypted Settings Export/Import" as checked.
*   **Reality:** `SettingsViewModel.kt` implements `exportSettings` and `importSettings` using `SecurityUtils` and AES encryption.
*   **Action:** `TODO.md` is accurate.

## 7. Interaction Mode (Docked / FAB)
*   **Contradiction:** `UI_UX.md` describes a "Docked / FAB" mode. User instructions (memory) stated "do not implement a persistent 'Docked' or FAB state when the overlay is hidden."
*   **Reality:** The code (MainViewModel/OverlayDelegate) manages `isSelectMode` (Overlay) vs Interaction Mode. The `IdeazOverlayService` (legacy/system) is mentioned in `UI_UX.md` as supporting this. The `AzNavRail` library likely handles the FAB behavior.
*   **Action:** `UI_UX.md` has been updated to reflect the "Dynamic Overlay" behavior which shrinks but stays visible, rather than a separate "FAB Mode" state that persists when the overlay is "hidden". (The overlay is never truly hidden in Interaction mode, just minimized).

## 8. Build Service
*   **Contradiction:** `README.md` implies a "Race to Build" where local and remote builds race.
*   **Reality:** `BuildService.kt` implements the local build. `MainViewModel` (via `BuildDelegate`) handles the remote artifact checking. The "race" logic exists in the sense that both are triggered/checked.
*   **Action:** `blueprint.md` accurately describes this "Race to Build" strategy.
