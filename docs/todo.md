# Peridium IDE: Phased Implementation Roadmap

This document outlines the practical, phased implementation plan for constructing the Peridium IDE, based on the detailed roadmap in the project's official `blueprint.md`.

---

### **Phase 1: The Build Pipeline Proof-of-Concept (POC)**
The primary goal of this phase is to validate the core technical assumption: that a standard Android application can be compiled, packaged, and installed entirely on-device using a custom toolchain.

- [ ] **1.1: Acquire and Prepare Build Tools**
- [ ] **1.2: Create the Build Service Project**
- [ ] **1.3: Implement the Core Build Script**
- [ ] **1.4: Implement Programmatic Installation**

---

### **Phase 2: The IDE Host and Project Management**
This phase focuses on building the user-facing application and establishing communication with the now-proven build service.

- [ ] **2.1: Develop the Host App UI**
- [ ] **2.2: Integrate Version Control (JGit)**
- [ ] **2.3: Establish Inter-Process Communication (AIDL)**
- [ ] **2.4: Implement the Build Console**

---

### **Phase 3: UI Inspection and Source Mapping**
This phase implements the first half of the core visual interaction loop: tapping a UI element to find its source code.

- [ ] **3.1: Implement the Accessibility Service**
- [ ] **3.2: Implement Inspector-to-Host IPC**
- [ ] **3.3: Generate the Source Map during the build**
- [ ] **3.4: Implement Source Lookup in the Host App**

---

### **Phase 4: AI Integration**
This phase completes the core feature loop by integrating the Jules API.

- [ ] **4.1: Integrate the API Client (Retrofit, Ktor, etc.)**
- [ ] **4.2: Implement the AI Prompt Workflow**
- [ ] **4.3: Implement Patch Application (JGit)**
- [ ] **4.4: Automate the Rebuild after patch application**

---

### **Phase 5: Refinement and Advanced Features**
The final phase transforms the functional prototype into a polished, performant, and feature-rich application.

- [ ] **5.1: Implement Incremental Builds**
- [ ] **5.2: Implement On-Device Dependency Resolution**
- [ ] **5.3: Implement the AI Debugger**
- [ ] **5.4: Enhance the Code Editor**
- [ ] **5.5: Polish and Test (Theming, UI/UX Review, Performance)**
