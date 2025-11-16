# IDEaz IDE: Documentation File Descriptions

This document provides a brief overview of the purpose of each documentation file in the `docs` folder for the intent-driven IDEaz IDE project.

-   **`UI_UX.md`**: Describes the "post-code" user experience, focusing on the "Interact" vs. "Select" modes, the **hybrid tap-and-drag selection model**, the "Live App" view, and the "Select and Instruct" user journey.

-   **`auth.md`**: Outlines the dual-layer security model: social sign-on for the app, and the "Bring Your Own Key" (BYOK) model for authenticating calls to the **Gemini API**. Notes that the **Jules Tools CLI** handles its own authentication.

-   **`blueprint.md`**: The **primary architectural document**. Details the 4 core components, the IPC strategy, the hybrid tap/drag selection model, and the AI abstraction layer (which routes between `JulesCliClient` and `GeminiApiClient`).

-   **`build_pipeline.md`**: A detailed, step-by-step breakdown of the "No-Gradle" on-device build system. **Crucially, this file explains the `jniLibs` bundling strategy for all native toolchain binaries.**

-   **`conduct.md`**: Establishes the Code of Conduct for all contributors to the IDEaz IDE project.

-   **`data_layer.md`**: Describes the dual data layer architecture: the "Invisible Repository" (Git) that acts as the source of truth for the user's app, and the local, on-device storage (EncryptedSharedPreferences, Room) used by the IDEaz IDE app itself.

-   **`fauxpas.md`**: A guide to common pitfalls, such as insecure API key storage, mixing the global and contextual log streams, and **ignoring the AI abstraction layer**.

-   **`file_descriptions.md`**: This file. It serves as a meta-document to help navigate the project's documentation.

-   **`misc.md`**: A catch-all document for future feature ideas (e.g., Visual Version Control) and open questions related to the intent-driven model.

-   **`performance.md`**: Focuses on the unique performance challenges of the on-device architecture, including the new UI rendering overhead in the `UIInspectionService`.

-   **`screens.md`**: Provides an overview of the minimal UI of the IDEaz IDE app itself, detailing the **hybrid Contextual Prompt/Log UI (with its cancel button)** and the "Global Console (Bottom Sheet)".

-   **`task_flow.md`**: Narrates the end-to-end user journey with concrete scenarios, explicitly describing the **separate flows for tap-to-select and drag-to-select**, and the **task cancellation flow**.

-   **`testing.md`**: Outlines the testing strategy, emphasizing E2E tests using `UI Automator` to validate the floating overlay UI and the AI routing logic.

-   **`todo.md`**: The phased implementation plan, now updated to reflect the dual-log system and the new contextual overlay UI in Phase 6.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/services/BuildService.kt`**: A background service that runs in a separate process to manage the on-device build toolchain. It receives build requests from the Host App and reports back the status and logs.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/BuildStep.kt`**: An interface that defines a contract for all the build steps.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/Aapt2Compile.kt`**: A class that implements the `BuildStep` interface and is responsible for compiling the Android resources using `aapt2`.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/Aapt2Link.kt`**: A class that implements the `BuildStep` interface and is responsible for linking the compiled resources and the `AndroidManifest.xml` to produce a preliminary `resources.apk` and the `R.java` file.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/KotlincCompile.kt`**: A class that implements the `BuildStep` interface and is responsible for compiling the Kotlin source code to JVM bytecode using `kotlinc`.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/D8Compile.kt`**: A class that implements the `BuildStep` interface and is responsible for converting the JVM bytecode into Android's `.dex` format using `d8`.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ApkBuild.kt`**: A class that implements the `BuildStep` interface and is responsible for packaging the final APK.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/ApkSign.kt`**: A class that implements the `BuildStep` interface and is responsible for signing the APK with a debug certificate using `apksigner`.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/buildlogic/BuildOrchestrator.kt`**: A class that is responsible for executing the build steps in the correct order.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/utils/ProcessExecutor.kt`**: A utility object that provides methods to execute command-line processes.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/utils/ToolManager.kt`**: A utility object that manages **getting the executable paths for native binaries (from `jniLibs`) and data files (from `assets`)**.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/ui/MainViewModel.kt`**: The ViewModel for the `MainActivity`. It manages the UI state, handles the `BuildService` connection, and orchestrates the build process. **It also routes AI requests, handles tap/drag contexts, and manages the cancel-task logic.**

- **`app/src/main/kotlin/com/hereliesaz/ideaz/models/SourceMapEntry.kt`**: A data class that represents an entry in the source map.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/utils/SourceMapParser.kt`**: A utility class that is responsible for parsing the `source_map.json` file.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/api/JulesCliClient.kt`**: A client for executing the **native `libjules.so` command-line tool**.

- **`app/src/main/kotlin/com/hereliesaz/ideaz/api/ApiClient.kt`**: A singleton object that provides a configured Retrofit instance for the `JulesApiService` (Web API).