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

## Recent Changes (Summary)
* **Refactor:** `MainViewModel` split into 6 Delegates. `ProjectScreen` split into sub-tabs.
* **Stability:** Fixed JNA Crash and Service ANR.
* **UI:** Updated `AzNavRail` components with `enabled`, `isLoading`, and `isError` states.