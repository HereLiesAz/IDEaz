# **Architectural Blueprint for IDEaz**

## **1. Executive Vision: A New Paradigm for Mobile Development**

IDEaz represents a fundamental leap forward in mobile application development, engineered to drastically compress the iteration cycle. The core thesis is the seamless integration of a visual, on-device UI inspection mechanism with the transformative power of generative artificial intelligence. This approach moves beyond the traditional, text-centric coding paradigm, introducing a more intuitive, interactive, and conversational model of software creation.

The user does not write code; they visually select an element in their running application. This action presents a **contextual AI prompt and log overlay**. The user describes the desired change, and an AI agent, powered by a **user-selected API (e.g., Jules Tools CLI or Gemini)**, handles the entire development lifecycle—code generation, compilation, and debugging—directly on the device.

---

## **2. System Architecture: A Multi-Process, On-Device Toolchain**

The architecture is founded on principles of robustness and performance, leveraging a multi-process design to isolate core components and prevent system-wide failures.

### **2.1 Architectural Philosophy: Process Isolation**

A monolithic architecture, where the IDE, build tools, and target application all reside in a single process, is inherently unstable. Therefore, the foundational design is a **multi-process architecture**, where each major component operates in its own distinct process boundary, communicating via well-defined Inter-Process Communication (IPC) channels.

### **2.2 The Four Core Components**

1.  **IDEaz Host Application:** The primary, user-facing application providing the UI for project management, code editing, and AI interaction. It is the central orchestrator of the entire system. It also provides a **global build/compile log** and a **contextless AI chat prompt**.
2.  **On-Device Build Service:** A background `Service` running in a separate process (`:build_process`). It manages the entire on-device build toolchain, receiving build requests from the Host App and reporting back status and logs.
3.  **UI Inspection Service:** A privileged `AccessibilityService` running in its own dedicated process. It is responsible for drawing the visual overlay on the target application, capturing **user taps (for element selection) and drags (for area selection)**, and **rendering the contextual AI prompt and log UI** for a selected element.
4.  **Target Application Process:** The user's application being developed. It runs in its own standard Android sandbox, completely isolated from the IDEaz components, ensuring an accurate representation of its real-world behavior.

### **2.3 Inter-Process Communication (IPC) Strategy**

Communication between the isolated processes will be handled using the **Android Interface Definition Language (AIDL)** for the `BuildService` and **system Broadcasts** for the `UIInspectionService`. This provides a robust framework for managing state and streaming data (logs, commands) across process boundaries.

---

## **3. The "No-Gradle" On-Device Build Pipeline**

The IDE eschews a full Gradle system in favor of a direct, scripted orchestration of core command-line build tools. This "No-Gradle" approach prioritizes speed, simplicity, and a minimal resource footprint.

For a complete breakdown of this system, see **`docs/build_pipeline.md`**.

---

## **4. Visual Interaction and Source Mapping**

The cornerstone of the IDE is the ability to visually select a UI element and have the AI instantly modify its source code.

### **4.1 Core Technology: The Android Accessibility Service**

The **Android AccessibilityService** is the core technology that enables the IDE to inspect the UI of the target application securely and without requiring root access. An AccessibilityService runs with elevated privileges that allow it to traverse the "accessibility node tree" (a representation of the on-screen UI elements) of the currently active window. This is the only non-root method available for this purpose.

### **4.2 Hybrid Selection: Tap vs. Drag**

The `UIInspectionService`'s overlay supports two distinct selection methods:

1.  **Tap-to-Select (Element):** If the user performs a simple tap, the service uses `findNodeAt()` to identify the deepest `AccessibilityNodeInfo` at that coordinate. It retrieves the element's `resourceId` and its on-screen `Rect` bounds.
2.  **Drag-to-Select (Area):** If the user drags their finger, the service draws a real-time selection box. On release, it captures the final `Rect` of the drawn area.

In both cases, the service creates a log overlay matching the `Rect` of the selection and a prompt overlay below it.

### **4.3 Build-Time Source Map**

To map a tapped element to its source code, the Build Service generates a `source_map.json` file during compilation, linking `resourceId` strings to their exact file path and line number. This map is *only* used for the Tap-to-Select flow.

### **4.4 Contextual Prompt Prefixing**

When the user submits a prompt, the `MainViewModel` constructs a "rich prompt" by *prefixing* the user's text with the context it received from the service:
* **On Tap:** `Context (for element [resourceId]): File: [path], Line: [num], Snippet: [code]... User Request: "[prompt]"`
* **On Drag:** `Context: Screen area Rect([coords])... User Request: "[prompt]"`

---

## **5. AI Integration and Abstraction**

The IDE will use an embedded **JGit** library to manage every project as a local Git repository. This is a prerequisite for AI models that operate on changesets.

### **5.1 AI Abstraction Layer**

The IDE is not tied to a single AI. The `SettingsViewModel` stores user preferences for which AI model (e.g., "Jules", "Gemini Flash") to use for specific tasks:
* Project Initialization
* Contextual (Overlay) Chat
* Contextless (Global) Chat

The `MainViewModel` reads these preferences and routes all AI requests to the appropriate client (e.g., `JulesCliClient` for Jules, or `GeminiApiClient` for Gemini).

### **5.2 The AI Workflow (Contextual)**

1.  **Commit Current State:** The IDE automatically commits the current state of the project.
2.  **Construct Rich Prompt:** The `MainViewModel` constructs the detailed, prefixed prompt as described in 4.4.
3.  **Route and Call AI:** The `MainViewModel` checks the user's settings for the **"Overlay Chat"** task and selects the assigned AI.
    * **If Jules:** It calls the `JulesCliClient`, which executes the `libjules.so` native binary with the prompt.
    * **If Gemini:** It calls the `GeminiApiClient`, which makes an HTTP request.
4.  **Stream Logs to Overlay:** As the AI works, all chat and activity logs are streamed *only* to the `UIInspectionService`'s floating log box. The log box contains a **Cancel (X) button** to terminate the task.
5.  **Receive and Apply Patch:** The AI's final output (a `gitPatch`) is received.
6.  **Trigger Rebuild:** The Host App sends an IPC message to the On-Device Build Service. All **build and compile logs** are streamed *only* to the Host App's main bottom sheet.