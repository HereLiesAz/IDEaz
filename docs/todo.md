# IDEaz: Phased Implementation Roadmap

This document outlines the practical, phased implementation plan for constructing the IDEaz, based on the detailed roadmap in the project's official `blueprint.md`.

---

### **Phase 1: The Build Pipeline Proof-of-Concept (POC)**
The primary goal of this phase is to validate the core technical assumption: that a standard Android application can be compiled, packaged, and installed entirely on-device using a custom toolchain.

- [x] **1.1: Acquire and Prepare Build Tools**
- [x] **1.2: Create the Build Service Project**
- [x] **1.3: Implement the Core Build Script**
- [x] **1.4: Implement Programmatic Installation**

---

### **Phase 2: The IDE Host and Project Management**
This phase focuses on building the user-facing application and establishing communication with the now-proven build service.

- [x] **2.1: Develop the Host App UI**
- [x] **2.2: Integrate Version Control (JGit)**
- [x] **2.3: Establish Inter-Process Communication (AIDL)**
- [x] **2.4: Implement the Build Console**

---

### **Phase 3: UI Inspection and Source Mapping**
This phase implements the first half of the core visual interaction loop: tapping a UI element to find its source code.

- [x] **3.1: Implement the Accessibility Service**
- [x] **3.2: Implement Inspector-to-Host IPC**
- [x] **3.3: Generate the Source Map during the build**
- [x] **3.4: Implement Source Lookup in the Host App**

---

### **Phase 4: AI Integration**
This phase completes the core feature loop by integrating the Jules API.

- [x] **4.1: Integrate the API Client (Retrofit, Ktor, etc.)**
    - Implemented `JulesApiClient` using Retrofit to interface with the Jules AI API, replacing runtime dependency on the unstable CLI.
- [x] **4.2: Implement the AI Prompt Workflow**
- [x] **4.3: Implement Patch Application (JGit)**
- [x] **4.4: Automate the Rebuild after patch application**

---

### **Phase 5: Refinement and Advanced Features**
The final phase transforms the functional prototype into a polished, performant, and feature-rich application.

- [x] **5.1: Implement Incremental Builds**
- [x] **5.2: Implement On-Device Dependency Resolution**
- [x] **5.3: Implement the AI Debugger**
- [x] **5.4: Enhance the Code Editor**
- [x] **5.5: Polish and Test (Theming, UI/UX Review, Performance)**

---

### **Phase 6: Advanced UI/UX and Background Operation Enhancements**
This phase focuses on improving the user experience during long-running background tasks, providing constant and clear feedback.

- [x] **6.1: Implement Live Output Bottom Card**
    - Create a reusable pull-up bottom card component.
    - Connect the card to the On-Device Build Service to stream the live **build and compile** logcat.
    - Connect the card to the Jules API client to display the live AI activity log for the **contextless, global AI chat prompt**.
- [x] **6.2: Implement Contextual AI Overlay UI**
    - Enhance `UIInspectionService` to render a floating UI (using `WindowManager`) when an element is selected.
    - This UI will consist of a prompt input box and a log view.
    - Establish a two-way IPC channel (e.g., Broadcasts) between `MainViewModel` and `UIInspectionService`.
    - When a user submits a prompt, this UI's log view will stream the **AI chat output** for that specific task.
    - The prompt input will remain available for the user to reply to AI clarifications.
- [x] **6.3: Implement Persistent Status Notification**
    - Enhance background services to manage a persistent notification.
    - The notification content should be updated to always show the most recent line of the **global build log** from the bottom card.

---

### **Phase 7: Project Templates and Multi-Platform Support**
This phase will broaden the IDE's appeal and capabilities by supporting a wider range of project types and development frameworks beyond the initial all-Kotlin focus. All implementations must adhere to the "post-code" editing model.

- [x] **7.1: Lay Groundwork for Multi-Platform Support**
    - [x] Update `MainViewModel` (acts as ProjectRepository) to support non-Android project structures (Web, React Native, Flutter).
    - [x] Create file structure and basic templates for:
        - [x] Web (HTML/CSS/JS)
        - [x] React Native (JS/TS)
        - [x] Flutter (Dart)

- [x] **7.2: Implement Web Design Support**
    - [x] Create a `WebBuildStep` for processing HTML/CSS.
    - [x] Implement a WebView-based runtime environment for the "post-code" overlay.
    - [x] Ensure visual selection works via DOM interaction.

- [ ] **7.3: Implement React Native Support**
    - [x] Create a `ReactNativeBuildStep` (uses `SimpleJsBundler` and Android shell template).
    - [x] Create Android shell template for React Native.
    - [x] Implement `SimpleJsBundler` with source map injection (`accessibilityLabel`).
    - [x] Adapt the `UIInspectionService` to handle React Native UI trees (or bridge integration).

- [ ] **7.4: Implement Flutter Support (Final Stage)**
    - [ ] Create a `FlutterBuildStep`.
    - [ ] Investigate and implement connection to the Flutter Inspector for UI selection.

---

### **Phase 8: Build System Overhaul Investigation**
This phase will explore the feasibility of replacing the current, custom-built build orchestration and dependency resolution system with a more powerful, all-in-one tool.

- [ ] **8.1: Investigate `labt` as a Build System Replacement**
    - **Goal:** Evaluate the "Lightweight Android Build Tool" (`labt`) as a potential replacement for the existing `BuildOrchestrator` and `DependencyResolver`.
    - **Potential Benefits:** `labt` is a Rust-based, all-in-one tool that handles dependency management, SDKs, and compilation. It's designed to be fast, lightweight, and offline-first, which aligns with the project's goals.
    - **Risks & Challenges:** The tool is currently marked as unstable (pre-v1). It is built with Rust and primarily targets Linux, so cross-compiling for Android and integrating it as a native binary will be a significant technical hurdle.
    - **Action:** Conduct a proof-of-concept to determine the viability of integrating `labt` into the IDEaz build process.