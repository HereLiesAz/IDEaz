# Report on Documentation Contradictions & Discrepancies

This report details "glaring contradictions" found between the project documentation, memory/instructions, and the actual codebase state.

## 1. The "No-Code" Vision vs. Reality

*   **Contradiction:** The documentation (`docs/blueprint.md`, `README.md`) strongly asserts that IDEaz is a "Post-Code" IDE where the user "never touches a file" and interacts primarily with the running application.
*   **Reality:** The codebase contains fully functional developer tools:
    *   `FileExplorerScreen.kt`: A read-write file explorer.
    *   `CodeEditor.kt`: A code editor with syntax highlighting.
    *   `FileContentScreen.kt`: A screen allowing users to edit and save file content.
*   **Resolution:** `docs/blueprint.md` has been updated to acknowledge these features as "Developer Tools" intended for power users and debugging, rather than primary development flow.

## 2. The `min-app` Module & "Repository-Less" Architecture

*   **Contradiction:** System memory stated that "The `min-app` project module implements a 'Repository-Less' architecture...".
*   **Reality:**
    *   No `min-app` directory exists in the codebase.
    *   `settings.gradle.kts` only includes the `:app` module.
    *   The architecture relies on local JGit repositories stored in `filesDir/projects/`.
*   **Status:** This appears to be a hallucinated or obsolete memory. The documentation correctly describes the current file-system-based architecture.

## 3. Versioning Instructions

*   **Contradiction:** `AGENTS.md` instructed agents to "update the `minor` or `patch` variables in `app/build.gradle.kts`".
*   **Reality:** `app/build.gradle.kts` reads version information from a root `version.properties` file. It does not contain hardcoded version variables.
*   **Resolution:** `AGENTS.md` has been updated to point to `version.properties`.

## 4. Jules CLI vs. API

*   **Contradiction:** `docs/jules-integration.md` (and memory) mentions the Jules CLI (`JulesCliClient`) as a component, but also notes it is unreliable.
*   **Reality:** `JulesCliClient.kt` exists but is largely unused in favor of `JulesApiClient.kt` (Retrofit implementation), which is used by `AIDelegate`.
*   **Status:** The documentation (`docs/jules-integration.md`) was already accurate in describing the CLI as "Legacy/Reference" and "Bypassed". No change needed.

## 5. Build System Confusion

*   **Contradiction:** Some documentation implies a "No-Gradle" build system on-device (`BuildService` implementing `Aapt2Compile`, etc.), while other parts discuss standard Gradle.
*   **Reality:** The project implements *both*. The `app` itself is built with Gradle, but it *contains* a custom build system (`com.hereliesaz.ideaz.buildlogic`) to build *user projects* on-device without Gradle.
*   **Status:** This is consistent with the `docs/architecture.md` describing the execution layer.
