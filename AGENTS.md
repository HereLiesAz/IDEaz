# Agent Instructions

**CRITICAL INSTRUCTIONS FOR ALL AI AGENTS:**

Before committing ANY changes, you **MUST** strictly adhere to the following workflow. **NO EXCEPTIONS.**

1.  **Code Review:** You must request and receive a complete code review.
2.  **Verify Build & Tests:** You must run a full build and ensure all tests pass. Use `./gradlew build` and `./gradlew testDebugUnitTest`.
3.  **Update Documentation:** You must update ALL relevant documentation to reflect your changes. This includes `TODO.md`, `file_descriptions.md`, and any other specific docs.
4.  **Update version:** Follow the versioning strategy below.
5.  **Commit:** Only AFTER steps 1-4 are successfully completed may you commit your changes.

## Versioning Strategy

* **Format:** `IDEaz-a.b.c.d.apk` (e.g., `IDEaz-1.0.0.14.apk`).
    * `a` (Prime): User controlled.
    * `b` (Minor): Incremented by Agents for **Major Features/Functions**.
    * `c` (Patch): Incremented by Agents for **Small Functions/Bug Fixes**.
    * `d` (Build): Incremented programmatically by CI.
* **Instruction:** When completing a task, you MUST update the `minor` or `patch` variables in `app/build.gradle.kts` appropriately.

---

## Documentation Index

The `docs/` folder contains the comprehensive documentation for this project. These files are an extension of this `AGENTS.md` and are **equally important**. You must read and understand them.

* **`docs/file_descriptions.md`**: A map of the codebase. Read this to know where things live.
* **`docs/architecture.md`**: Explains the Delegate pattern and the Overlay loop.
* **`docs/workflow.md`**: Explains the "Race to Build" and Git Ops.
* **`docs/UI_UX.md`**: Explains the Navigation Rail, Bubbles, and Overlays.
* **`docs/TODO.md`**: The current plan.
* **`docs/AGENT_GUIDE.md`**: Detailed guide for AI agents.
* **`docs/auth.md`**: Authentication mechanisms and specifications.
* **`docs/blueprint.md`**: The core vision and roadmap.
* **`docs/build_pipeline.md`**: Details on the build process and CI/CD.
* **`docs/conduct.md`**: Code of conduct.
* **`docs/data_layer.md`**: Data storage and management.
* **`docs/error_handling.md`**: Strategies for handling and reporting errors.
* **`docs/fauxpas.md`**: Common mistakes and anti-patterns to avoid.
* **`docs/jules-integration.md`**: Integration details for Jules AI.
* **`docs/manifest.md`**: Android Manifest configuration details.
* **`docs/misc.md`**: Miscellaneous documentation.
* **`docs/performance.md`**: Performance guidelines and optimization.
* **`docs/platform_decision_helper.md`**: Helper for making platform-specific decisions.
* **`docs/react_native_implementation_plan.md`**: Plan for React Native support.
* **`docs/screens.md`**: Overview of application screens.
* **`docs/task_flow.md`**: Flow of tasks within the application.
* **`docs/testing.md`**: Testing strategies and requirements.
* **`docs/todo.md`**: Alternate TODO list.

## Recent Changes (Summary)
* **Refactor:** `MainViewModel` split into 6 Delegates. `ProjectScreen` split into sub-tabs.
* **Stability:** Fixed JNA Crash and Service ANR.
* **UI:** Updated `AzNavRail` components with `enabled`, `isLoading`, and `isError` states.
* **UI:** Updated `AzNavRail` to 5.2 (Dynamic Overlay).