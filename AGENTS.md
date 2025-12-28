# Agent Instructions

**CRITICAL INSTRUCTIONS FOR ALL AI AGENTS:**

Before committing ANY changes, you **MUST** strictly adhere to the following workflow. **NO EXCEPTIONS.**

1.  **Code Review:** You must request and receive a complete code review.
2.  **Verify Build & Tests:** You must run a full build and ensure all tests pass. Use `./gradlew build` or specific task like `./gradlew :app:assembleDebug`.
3.  **Update Documentation:** You must update ALL relevant documentation to reflect your changes. This includes `TODO.md`, `file_descriptions.md`, and any other specific docs.
4.  **Update version:** Follow the versioning strategy below.
5.  **Commit:** Only AFTER steps 1-4 are successfully completed may you commit your changes.

## Versioning Strategy

* **Format:** `IDEaz-a.b.c.d.apk` (e.g., `IDEaz-1.0.0.14.apk`).
    * `a` (Prime): User controlled.
    * `b` (Minor): Incremented by Agents for **Major Features/Functions**.
    * `c` (Patch): Incremented by Agents for **Small Functions/Bug Fixes**.
    * `d` (Build): Incremented programmatically by CI.
* **Instruction:** When completing a task, you MUST update the `minor` or `patch` values in `version.properties` appropriately.

---

## Critical Known Issues / Discrepancies
*   **Tabs Order:** Documentation previously listed Project Screen tabs in incorrect order. The correct order is Setup, Load, Clone.

## Documentation Index

The `docs/` folder contains the comprehensive documentation for this project. These files are an extension of this `AGENTS.md` and are **equally important**. You must read and understand them.

*   **`docs/file_descriptions.md`**: A map of the codebase.
*   **`docs/AGENT_GUIDE.md`**: Detailed guide for AI agents.
*   **`docs/TODO.md`**: The master checklist.
*   **`docs/UI_UX.md`**: Visual design and interaction patterns.
*   **`docs/architecture.md`**: High-level system architecture.
*   **`docs/auth.md`**: Authentication mechanisms.
*   **`docs/blueprint.md`**: Core vision and roadmap.
*   **`docs/build_pipeline.md`**: Details on the build process.
*   **`docs/conduct.md`**: Code of conduct for agents.
*   **`docs/data_layer.md`**: Data storage, API, and state management.
*   **`docs/error_handling.md`**: Strategy for handling and reporting errors.
*   **`docs/fauxpas.md`**: Common mistakes and anti-patterns.
*   **`docs/jules-integration.md`**: Details on Jules API integration.
*   **`docs/manifest.md`**: AndroidManifest explanation.
*   **`docs/misc.md`**: Miscellaneous info (templates, logs).
*   **`docs/performance.md`**: Performance guidelines.
*   **`docs/platform_decision_helper.md`**: Guide for platform support decisions.
*   **`docs/react_native_implementation_plan.md`**: (Partial/Stalled) plan for RN support.
*   **`docs/screens.md`**: Overview of application screens.
*   **`docs/task_flow.md`**: Operational workflows.
*   **`docs/testing.md`**: Testing strategies and requirements.
*   **`docs/workflow.md`**: CI/CD and Build processes.

## Recent Changes (Summary)
*   **Documentation:** Updated `docs/` to match codebase reality (Missing services, tab orders).
*   **Refactor:** `MainViewModel` split into 6 Delegates. `ProjectScreen` split into sub-tabs.
*   **Stability:** Fixed JNA Crash and Service ANR.
*   **UI:** Updated `AzNavRail` to 5.3 (Dynamic Overlay).
*   **Fix:** `IdeBottomSheet` is now always available in `MainScreen`.
