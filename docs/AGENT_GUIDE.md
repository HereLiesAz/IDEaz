# Agent Guide

## Core Directives

*   **Zero-Trust Environment:** The execution environment is unstable. Files may disappear, and the environment may reset. Always verify file existence and content before acting. Use `read_file` or `ls` frequently.
*   **Verification:** Never assume an action succeeded. Always verify. For example, if you create a file, check if it exists. If you change code, check the diff.
*   **Process Isolation:** Respect the multi-process architecture (Host, Build Service, UI Inspection Service). Code running in one process cannot directly access objects in another. Use AIDL or Broadcasts.
*   **"No-Gradle" Pipeline:** Understand that the on-device build system is a custom orchestration of CLI tools (`aapt2`, `d8`, `kotlinc`). Do not rely on Gradle features for the *on-device* logic, only for the *host* project build.
*   **Dependencies:** Be extremely careful with dependencies. The project uses a complex mix of local JARs, AARs, and Maven artifacts.

## Workflow

1.  **Understand the Goal:** Read the prompt and the `TODO.md` carefully.
2.  **Explore:** Use `list_files` and `read_file` to understand the relevant code. Do not rely solely on your training data; the codebase is the source of truth.
3.  **Plan:** Create a step-by-step plan. Include verification steps.
4.  **Execute:**
    *   Make small, atomic changes.
    *   Verify each change immediately.
    *   Handle errors robustly.
5.  **Test:** Run `./gradlew testDebugUnitTest` to verify logic. Run `./gradlew build` to verify compilation.
6.  **Document:** Update `docs/` files to reflect your changes.
7.  **Submit:** Request review and submit.

## Common Pitfalls

*   **File Paths:** Be careful with file paths. Use `ProjectAnalyzer` to resolve paths dynamically where possible.
*   **Exceptions:** Catch `Throwable`, not just `Exception`, especially in the Build Service, to catch `NoClassDefFoundError` and other runtime errors.
*   **Concurrency:** Use `Dispatchers.IO` for disk/network operations. Be aware of `MainViewModel`'s `gitMutex`.
*   **UI Updates:** All UI updates must happen on the Main thread. Use `withContext(Dispatchers.Main)` if necessary, or update `StateFlow`s which are observed by Compose.

## Documentation Maintenance

*   **TODO.md:** Mark tasks as complete (`[x]`) as you finish them. Add new tasks if you discover necessary work.
*   **file_descriptions.md:** If you add a new file, add it here.
*   **screens.md:** If you modify a screen, update its description.

## Environment Setup

*   The environment usually lacks a full Android SDK installation for the *host* machine (the one running the agent), but the project includes `setup_env.sh` to configure it.
*   The *target* device (where the app runs) has its own set of tools managed by `ToolManager`. Do not confuse the two.
