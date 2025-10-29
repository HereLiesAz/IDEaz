# Cortex IDE: Documentation File Descriptions

This document provides a brief overview of the purpose of each documentation file in the `docs` folder for the intent-driven Cortex IDE project.

-   **`UI_UX.md`**: Describes the "post-code" user experience, focusing on the "Live App" view, the "Cortex Overlay" for interaction, and the "Select and Instruct" user journey.

-   **`auth.md`**: Outlines the dual-layer security model: standard social sign-on for user authentication to the Cortex IDE app, and the "Bring Your Own Key" (BYOK) model for authenticating calls to the Jules API.

-   **`conduct.md`**: Establishes the Code of Conduct for all contributors to the Cortex IDE project.

-   **`data_layer.md`**: Describes the dual data layer architecture: the "Invisible Repository" (Git) that acts as the source of truth for the user's app, and the local, on-device storage (EncryptedSharedPreferences, Room) used by the Cortex IDE app itself.

-   **`fauxpas.md`**: A guide to common pitfalls in the "Screenshot-First" architecture, such as insecure API key storage and providing poor user feedback.

-   **`file_descriptions.md`**: This file. It serves as a meta-document to help navigate the project's documentation.

-   **`misc.md`**: A catch-all document for future feature ideas (e.g., Visual Version Control) and open questions related to the intent-driven model.

-   **`performance.md`**: Focuses on the unique performance challenges of the on-device architecture, such as the speed of the `Git -> Compile -> Relaunch` loop and the responsiveness of the visual overlay.

-   **`screens.md`**: Provides an overview of the minimal UI of the Cortex IDE app itself, including the "Live App View," the "Cortex Overlay," and the "Cortex Hub" settings screen.

-   **`task_flow.md`**: Narrates the end-to-end user journey with concrete scenarios, including a successful visual change and an automated debugging loop where the AI corrects its own compile error.

-   **`testing.md`**: Outlines the testing strategy for the "Screenshot-First" architecture, emphasizing E2E tests using `UI Automator`.

-   **`workflow.md`**: Defines the development workflow (GitFlow) for the Cortex IDE project itself, and clarifies how it differs from the internal, automated workflow used by the AI agent.
