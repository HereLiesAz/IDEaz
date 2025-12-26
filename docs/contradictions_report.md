# Audit Report & Contradictions

## Overview
This document serves as a record of discrepancies found between the documentation and the codebase as of Phase 11. It highlights areas where the implementation has diverged from the original plan or where documentation has lagged behind refactoring efforts.

## Critical Contradictions

### 1. Zipline Hybrid Host (Phase 11)
*   **Documentation:** `docs/TODO.md` implies that Zipline integration for the Hybrid Host architecture is largely implemented, with only the final "Hot Reload & Runtime" step blocked. `docs/file_descriptions.md` lists Zipline-related build logic.
*   **Codebase:** `MainViewModel.kt` explicitly disables the Zipline loader (`ziplineLoader.loadOnce`) due to API deprecation issues (`loadOnce`/`load` are deprecated/removed in the version used).
*   **Status:** The feature is technically present in the codebase (toolchain, signing, manifest generation) but is **functionally disabled** at the runtime entry point. This represents a major blocker for the "Hybrid Host" vision.

### 2. Service Architecture Naming
*   **Documentation:** Various documents (`TODO.md`, `performance.md`, `misc.md`) referred to `UIInspectionService`.
*   **Codebase:** The functionality was split and renamed early in development:
    *   `IdeazAccessibilityService`: Handles the Accessibility API interactions for inspecting UI nodes.
    *   `IdeazOverlayService`: Handles the visual overlay window and user interaction.
*   **Status:** Documentation has been updated to reflect these accurate names.

## Minor Discrepancies

### 1. Documentation Index
*   **Status:** `AGENTS.md` and `docs/file_descriptions.md` were audited and are now consistent with the filesystem.

## Recommendations
1.  **Resolve Zipline Blocker:** The next major engineering task must be to upgrade the Zipline dependency to a stable version and refactor `MainViewModel` to use the non-deprecated API, or pivot to an alternative hot-reload mechanism.
2.  **Continuous Documentation:** Agents must strictly adhere to the "Update Documentation" step in `AGENTS.md` to prevent future drift, especially during large refactors like the `MainViewModel` delegation.
