# **Architectural Blueprint for IDEaz**

## **1. Executive Vision: A New Paradigm for Mobile Development**

IDEaz represents a fundamental leap forward in mobile application development, engineered to drastically compress the iteration cycle. The core thesis is the seamless integration of a visual, on-device UI inspection mechanism with the transformative power of generative artificial intelligence. This approach moves beyond the traditional, text-centric coding paradigm, introducing a more intuitive, interactive, and conversational model of software creation.

The user does not write code; they visually select an element in their running application. This action presents a **contextual AI prompt and log overlay**. The user describes the desired change, and an AI agent, powered by a **user-selected API (e.g., Jules or Gemini)**, handles the entire development lifecycle—code generation, compilation, and debugging—directly on the device.

---

## **2. System Architecture: A Multi-Process, On-Device Toolchain**

The architecture is founded on principles of robustness and performance, leveraging a multi-process design to isolate core components and prevent system-wide failures.

### **2.1 Architectural Philosophy: Process Isolation**

A monolithic architecture, where the IDE, build tools, and target application all reside in a single process, is inherently unstable. Therefore, the foundational design is a **multi-process architecture**, where each major component operates in its own distinct process boundary, communicating via well-defined Inter-Process Communication (IPC) channels.

### **2.2 The Four Core Components**

1.  **IDEaz Host Application:** The primary, user-facing application providing the UI for project management, code editing, and AI interaction. It is the central orchestrator of the entire system. It also provides a **global build/compile log** and a **contextless AI chat prompt**.
2.  **On-Device Build Service:** A background `Service` running in a separate process (`:build_process`). It manages the entire on-device build toolchain, receiving build requests from the Host App and reporting back status and logs.
3.  **UI Inspection Service:** A privileged `AccessibilityService` running in its own dedicated process. It is responsible for drawing the visual overlay on the target application, capturing user input, querying the view hierarchy, and **rendering the contextual AI prompt and log UI** for a selected element.
4.  **Target Application Process:** The user's application being developed. It runs in its own standard Android sandbox, completely isolated from the IDEaz components, ensuring an accurate representation of its real-world behavior.

### **2.3 Inter-Process Communication (IPC) Strategy**

Communication between the isolated processes will be handled using the **Android Interface Definition Language (AIDL)** for the `BuildService` and **system Broadcasts** for the `UIInspectionService`. This provides a robust framework for managing state and streaming data (logs, commands) across process boundaries.

---

## **3. The "No-Gradle" On-Device Build Pipeline**

To achieve the necessary speed and low resource footprint for on-device compilation, IDEaz will eschew a full Gradle system in favor of a direct, scripted orchestration of core command-line build tools.

### **3.1 Bundled Toolchain**

The IDE will bundle its own native binaries for the essential Android build tools, which will be extracted to the app's private storage on first launch:

* **aapt2:** For compiling and linking Android resources.
* **kotlinc-embeddable:** For compiling Kotlin source code to JVM bytecode.
* **d8:** For converting JVM bytecode into Android's .dex format.
* **apksigner:** For signing the final APK with a debug certificate.

### **3.2 The Build Sequence**

The On-Device Build Service will execute a precise sequence of command-line invocations:

1.  **Resource Compilation (aapt2 compile):** Compiles all XML resources into an intermediate `.flat` format.
2.  **Resource Linking (aapt2 link):** Links the compiled resources and the `AndroidManifest.xml` to produce a preliminary `resources.apk` and, critically, the `R.java` file.
3.  **Source Code Compilation (kotlinc):** Compiles the user's source code and the generated `R.java` into `.class` files.
4.  **Dexing (d8):** Converts all `.class` files into one or more `classes.dex` files.
5.  **Final APK Packaging:** Creates a final APK by combining the `resources.apk` and the `classes.dex` file(s).
6.  **Signing (apksigner):** Signs the APK with a bundled debug keystore.
7.  **Installation:** Triggers the system's package installer to install the newly built APK.

### **3.3 Dependency Resolution & Incremental Builds**

* **Dependency Management:** Dependencies will be declared in a simple `dependencies.toml` file. The Build Service will include a lightweight Maven artifact resolver to download and cache libraries from Maven Central.
* **Incremental Builds:** To achieve near-instant rebuilds, the Build Service will implement a robust incremental build system by storing and comparing file checksums (SHA-256 hashes), intelligently skipping unchanged steps in the build sequence.

---

## **4. Visual Interaction and Source Mapping**

The cornerstone of the IDE is the ability to visually select a UI element and have the AI instantly modify its source code.

### **4.1 Core Technology: Accessibility Service**

The **Android AccessibilityService** is the core technology that enables the IDE to inspect the UI of the target application securely and without requiring root access.

### **4.2 Element Selection and Source Mapping**

1.  **Invisible Overlay:** The UI Inspection Service will draw a transparent overlay to intercept touch events.
2.  **Node Identification:** When the user taps the screen, the service will use the event coordinates to find the specific `AccessibilityNodeInfo` object corresponding to the UI element at that location.
3.  **Build-Time Source Map:** To map this runtime view to its source code definition, the Build Service will generate a `source_map.json` file during compilation. This map will contain a direct link between a resource ID (e.g., `"login_button"`) and its exact location (file path and line number) in the XML layout file.
4.  **Contextual UI Trigger:** The Host App receives the `RESOURCE_ID`, looks up its source, and then commands the `UIInspectionService` (via Broadcast) to **display the floating prompt/log UI** near the selected element.

---

## **5. AI Integration and Abstraction**

The IDE will use an embedded **JGit** library to manage every project as a local Git repository. This is a prerequisite for AI models that operate on changesets.

### **5.1 AI Abstraction Layer**

The IDE is not tied to a single AI. The `SettingsViewModel` stores user preferences for which AI model (e.g., "Jules", "Gemini Flash") to use for specific tasks:
* Project Initialization
* Contextual (Overlay) Chat
* Contextless (Global) Chat

The `MainViewModel` reads these preferences and routes all AI requests to the appropriate client (e.g., `ApiClient` for Jules, or a future Gemini client). It also ensures the required API key for the selected service is present before sending a request.

### **5.2 The AI Workflow (Contextual)**

1.  **Commit Current State:** The IDE automatically commits the current state of the project.
2.  **Construct Rich Prompt:** The IDE constructs a detailed prompt containing the user's natural language instruction (from the overlay prompt) and the code context from the source map.
3.  **Route and Call AI:** The `MainViewModel` checks the user's settings for the **"Overlay Chat"** task and selects the assigned AI (e.g., Jules). It makes the API call.
4.  **Stream Logs to Overlay:** As the AI works, all chat and activity logs are streamed *only* to the `UIInspectionService`'s floating log box. If the AI needs clarification, the prompt box in the overlay is re-enabled.
5.  **Receive and Apply Patch:** The AI's final output (a `gitPatch`) is received by the Host App.
6.  **Trigger Rebuild:** The Host App sends an IPC message to the On-Device Build Service. All **build and compile logs** are streamed *only* to the Host App's main bottom sheet.