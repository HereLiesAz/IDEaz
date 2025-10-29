# Peridium IDE: Documentation File Descriptions

This document provides a brief overview of the purpose of each documentation file in the `docs` folder for the Peridium IDE project.

-   **`UI_UX.md`**: Describes the "post-code" user experience, focusing on the "Target Application" view, the `AccessibilityService`-based "Peridium Overlay," and the "Select and Instruct" user journey.

-   **`auth.md`**: Outlines the security model, which is now primarily focused on the "Bring Your Own Key" (BYOK) model for authenticating calls to the Jules API, as user accounts for the IDE itself are not a V1 requirement.

-   **`conduct.md`**: Establishes the Code of Conduct for all contributors to the Peridium IDE project.

-   **`data_layer.md`**: Describes the data layer architecture, focusing on the on-device Git repository (managed by JGit) that acts as the source of truth for the user's app, and the local storage (EncryptedSharedPreferences) used by the Peridium IDE Host App.

-   **`fauxpas.md`**: A guide to common pitfalls in the multi-process architecture, such as mishandling IPC calls and providing poor AI context.

-   **`file_descriptions.md`**: This file. It serves as a meta-document to help navigate the project's documentation.

-   **`misc.md`**: A catch-all document for future feature ideas and open questions.

-   **`performance.md`**: Focuses on the unique performance challenges of the on-device architecture, such as the speed of the "No-Gradle" build pipeline and the efficiency of the `AccessibilityService`.

-   **`screens.md`**: Provides an overview of the UI of the Peridium IDE Host App itself, including the main workspace, code editor, and build console.

-   **`task_flow.md`**: Narrates the end-to-end user journey with concrete scenarios, including a successful visual change and an automated debugging loop.

-   **`testing.md`**: Outlines the testing strategy, emphasizing IPC testing between the services and E2E tests for the full visual interaction and build loop.

-   **`workflow.md`**: Defines the development workflow (GitFlow) for the Peridium IDE project.
