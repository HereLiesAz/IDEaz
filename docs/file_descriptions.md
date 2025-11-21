# IDEaz IDE: Documentation File Descriptions

This document provides a brief overview of the purpose of each documentation file in the `docs` folder for the intent-driven IDEaz IDE project.

-   **`UI_UX.md`**: Describes the "post-code" user experience, focusing on the "Interact" vs. "Select" modes, the **hybrid tap-and-drag selection model**, the "Live App" view, and the "Select and Instruct" user journey.

-   **`auth.md`**: Outlines the dual-layer security model: social sign-on for the app, and the "Bring Your Own Key" (BYOK) model for authenticating calls to the **Gemini API** and **GitHub API**.

-   **`blueprint.md`**: The **primary architectural document**. Details the 4 core components, the IPC strategy, the hybrid tap/drag selection model, and the AI abstraction layer.

-   **`build_pipeline.md`**: A detailed, step-by-step breakdown of the "No-Gradle" on-device build system. **Crucially, this file explains the `jniLibs` bundling strategy and the exact execution order (Compile -> Link -> Dex -> Sign -> SourceMap).**

-   **`conduct.md`**: Establishes the Code of Conduct for all contributors to the IDEaz IDE project.

-   **`data_layer.md`**: Describes the dual data layer architecture: the "Invisible Repository" (Git) that acts as the source of truth for the user's app, and the local, on-device storage.

-   **`fauxpas.md`**: A guide to common pitfalls, such as insecure API key storage, mixing log streams, and **silencing internal IDE errors**.

-   **`file_descriptions.md`**: This file. It serves as a meta-document to help navigate the project's documentation.

-   **`misc.md`**: A catch-all document for future feature ideas (e.g., Visual Version Control) and open questions related to the intent-driven model.

-   **`performance.md`**: Focuses on the unique performance challenges of the on-device architecture, including the new UI rendering overhead in the `UIInspectionService`.

-   **`screens.md`**: Provides an overview of the minimal UI of the IDEaz IDE app itself, detailing the **hybrid Contextual Prompt/Log UI** and the "Global Console (Bottom Sheet)".

-   **`task_flow.md`**: Narrates the end-to-end user journey with concrete scenarios, explicitly describing the **auto-launch behavior** after a build and the **automated bug reporting** flow.

-   **`testing.md`**: Outlines the testing strategy, emphasizing E2E tests using `UI Automator`.

-   **`todo.md`**: The phased implementation plan, updated with completed status for Phase 6 and robustness tasks.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/services/BuildService.kt`**: A background service that manages the on-device build toolchain. Corrected to execute `GenerateSourceMap` last.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/BuildStep.kt`**: Interface for build steps.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/*.kt`**: Individual build steps (Aapt2Compile, Aapt2Link, etc.). `Aapt2Link` now includes validation for `android.jar`.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/utils/ToolManager.kt`**: Utility for managing native tools and assets. Now includes logic to validate and repair corrupt/empty asset files.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/utils/GithubIssueReporter.kt`**: **New utility** that automatically reports internal IDE exceptions to GitHub issues via API or browser fallback.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt`**: The ViewModel for `MainActivity`. Orchestrates the build, routes AI requests, and now **handles internal errors using `GithubIssueReporter`**.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/MainActivity.kt`**: The entry point. Now includes a `BroadcastReceiver` to **auto-launch the user's app** upon successful installation.