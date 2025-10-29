# IDEaz: Documentation File Descriptions

This document provides a brief overview of the purpose of each documentation file in the `docs` folder for the intent-driven IDEaz project.

-   **`UI_UX.md`**: Describes the "post-code" user experience, focusing on the "Live App" view, the "IDEaz Overlay" for interaction, and the "Select and Instruct" user journey.

-   **`auth.md`**: Outlines the dual-layer security model: standard social sign-on for user authentication to the IDEaz app, and the "Bring Your Own Key" (BYOK) model for authenticating calls to the Jules API.

-   **`conduct.md`**: Establishes the Code of Conduct for all contributors to the IDEaz project.

-   **`data_layer.md`**: Describes the dual data layer architecture: the "Invisible Repository" (Git) that acts as the source of truth for the user's app, and the local, on-device storage (EncryptedSharedPreferences, Room) used by the IDEaz app itself.

-   **`fauxpas.md`**: A guide to common pitfalls in the "Screenshot-First" architecture, such as insecure API key storage and providing poor user feedback.

-   **`file_descriptions.md`**: This file. It serves as a meta-document to help navigate the project's documentation.

-   **`misc.md`**: A catch-all document for future feature ideas (e.g., Visual Version Control) and open questions related to the intent-driven model.

-   **`performance.md`**: Focuses on the unique performance challenges of the on-device architecture, such as the speed of the `Git -> Compile -> Relaunch` loop and the responsiveness of the visual overlay.

-   **`screens.md`**: Provides an overview of the minimal UI of the IDEaz app itself, including the "Live App View," the "IDEaz Overlay," and the "IDEaz Hub" settings screen.

-   **`task_flow.md`**: Narrates the end-to-end user journey with concrete scenarios, including a successful visual change and an automated debugging loop where the AI corrects its own compile error.

-   **`testing.md`**: Outlines the testing strategy for the "Screenshot-First" architecture, emphasizing E2E tests using `UI Automator`.

-   **`workflow.md`**: Defines the development workflow (GitFlow) for the IDEaz project itself, and clarifies how it differs from the internal, automated workflow used by the AI agent.

- **`app/src/main/java/com.hereliesaz.ideaz/services/BuildService.kt`**: A background service that runs in a separate process to manage the on-device build toolchain. It receives build requests from the Host App and reports back the status and logs.

- **`app/src/main/java/com.hereliesaz.ideaz/buildlogic/BuildStep.kt`**: An interface that defines a contract for all the build steps, with an `execute` method that returns whether the step was successful.

- **`app/src/main/java/com.hereliesaz.ideaz/buildlogic/Aapt2Compile.kt`**: A class that implements the `BuildStep` interface and is responsible for compiling the Android resources using `aapt2`.

- **`app/src/main/java/com.hereliesaz.ideaz/buildlogic/Aapt2Link.kt`**: A class that implements the `BuildStep` interface and is responsible for linking the compiled resources and the `AndroidManifest.xml` to produce a preliminary `resources.apk` and the `R.java` file.

- **`app/src/main/java/com.hereliesaz.ideaz/buildlogic/KotlincCompile.kt`**: A class that implements the `BuildStep` interface and is responsible for compiling the Kotlin source code to JVM bytecode using `kotlinc`.

- **`app/src/main/java/com.hereliesaz.ideaz/buildlogic/D8Compile.kt`**: A class that implements the `BuildStep` interface and is responsible for converting the JVM bytecode into Android's `.dex` format using `d8`.

- **`app/src/main/java/com.hereliesaz.ideaz/buildlogic/ApkBuild.kt`**: A class that implements the `BuildStep` interface and is responsible for packaging the final APK.

- **`app/src/main/java/com.hereliesaz.ideaz/buildlogic/ApkSign.kt`**: A class that implements the `BuildStep` interface and is responsible for signing the APK with a debug certificate using `apksigner`.

- **`app/src/main/java/com.hereliesaz.ideaz/buildlogic/BuildOrchestrator.kt`**: A class that is responsible for executing the build steps in the correct order.

- **`app/src/main/java/com.hereliesaz.ideaz/utils/CommandLineUtils.kt`**: A utility object that provides methods to execute command-line processes.

- **`app/src/main/java/com.hereliesaz.ideaz/utils/ToolManager.kt`**: A utility object that manages the extraction of build tools from the app's assets.
