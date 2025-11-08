# IDEaz: Phased Implementation Roadmap

This document outlines the practical, phased implementation plan for constructing IDEaz, based on the detailed roadmap in the project's official documentation.

---

### **Phase 1: The Build Pipeline Proof-of-Concept (POC)**

- [x] **1.1: Acquire and Prepare Build Tools**
- [x] **1.2: Create the Build Service Project**
- [x] **1.3: Implement the Core Build Script**
- [x] **1.4: Implement Programmatic Installation**

---

### **Phase 2: The IDE Host and Project Management**

- [x] **2.1: Develop the Host App UI**
- [x] **2.2: Integrate Version Control (JGit)**
- [x] **2.3: Establish Inter-Process Communication (AIDL)**
- [x] **2.4: Implement the Build Console**

---

### **Phase 3: UI Inspection and Source Mapping**

- [x] **3.1: Implement the Accessibility Service**
- [x] **3.2: Implement Inspector-to-Host IPC**
- [x] **3.3: Generate the Source Map during the build**
- [x] **3.4: Implement Source Lookup in the Host App**

---

### **Phase 4: AI Integration**

- [x] **4.1: Integrate the API Client (Retrofit)**
- [x] **4.2: Implement the AI Prompt Workflow**
- [x] **4.3: Implement Patch Application (JGit)**
- [x] **4.4: Automate the Rebuild after patch application**

---

### **Phase 5: Refinement and Advanced Features**

- [x] **5.1: Implement Incremental Builds**
- [x] **5.2: Implement On-Device Dependency Resolution**
- [x] **5.3: Implement the AI Debugger**
- [x] **5.4: Enhance the Code Editor**
- [x] **5.5: Polish and Test (Theming, UI/UX Review, Performance)**

---

### **Phase 6: Advanced UI/UX and Background Operation Enhancements**

- [x] **6.1: Implement Live Output Bottom Card**
- [x] **6.2: Implement Contextual AI Overlay UI**
- [x] **6.3: Implement Persistent Status Notification**

---

### **Phase 7: Core Feature Implementation and Security**

- [ ] **7.1: Secure API Key Storage**
    - [ ] Add the `androidx.security:security-crypto` dependency.
    - [ ] Create a `SecureStorageManager` class that uses `EncryptedSharedPreferences`.
    - [ ] Refactor `SettingsViewModel` to use `SecureStorageManager` for saving and retrieving all API keys.
- [ ] **7.2: Implement AI Abstraction Layer**
    - [ ] Create an `AiClient` interface with a `generatePatch(prompt: String): Flow<AiState>` method.
    - [ ] Create `JulesAiClient` and `GeminiAiClient` classes that implement the `AiClient` interface.
    - [ ] Create an `AiManager` class that holds instances of the clients and a `getAssignedClient(task: AiTask): AiClient` method.
    - [ ] Refactor `MainViewModel` to use the `AiManager` for all AI operations, removing direct calls to `ApiClient` and `GeminiApiClient`.
- [ ] **7.3: Implement Full Gemini Workflow**
    - [ ] Engineer a system prompt for the `GeminiAiClient` that instructs the model to return only a `git diff` compatible patch.
    - [ ] Implement response parsing in `GeminiAiClient` to extract the patch from the model's text response.
    - [ ] Ensure the patch is correctly applied in the `MainViewModel`'s `applyPatch` function.
- [ ] **7.4: Implement AI Assignment UI**
    - [ ] In `SettingsScreen.kt`, create a new section for "AI Assignments".
    - [ ] For each task in `SettingsViewModel.aiTasks`, create a dropdown menu (`ExposedDropdownMenuBox`) that allows the user to select from `AiModels.availableModels`.
    - [ ] Ensure the user's selection is saved using `SettingsViewModel.saveAiAssignment`.
- [ ] **7.5: Implement UI for "Interact" vs. "Select" Modes**
    - [ ] In the `IdeNavRail`, add a toggle button that switches between "Interact" and "Select" modes.
    - [ ] The button should call `MainViewModel.startInspection()` and `MainViewModel.stopInspection()`.
    - [ ] The bottom sheet gesture should also trigger these mode changes.
- [ ] **7.6: Implement Drag-to-Select (Area) Context**
    - [ ] The `UIInspectionService` already detects drags. Ensure it broadcasts the final `Rect` to the `MainViewModel`.
    - [ ] In `MainViewModel.onRectPromptSubmitted`, ensure the rich prompt correctly includes the screen area context.
- [ ] **7.7: Implement Task Cancellation UI and Logic**
    - [ ] Add a visible "X" (`Icons.Default.Close`) button to the corner of the floating log overlay UI.
    - [ ] Tapping the button should call `MainViewModel.requestCancelTask()`.
    - [ ] Ensure the confirmation dialog logic in `MainViewModel` correctly cancels the `contextualTaskJob`.

---

### **Phase 8: Social Authentication and Finalization**

- [ ] **8.1: Implement Social Sign-On**
    - [ ] Add the Google Sign-In for Android library dependency.
    - [ ] Create a `LoginActivity` that handles the Google Sign-In flow.
    - [ ] On successful sign-in, navigate to the `MainActivity`.
- [ ] **8.2: Implement Room Database for Settings**
    - [ ] Create a `Settings` entity and a `SettingsDao`.
    - [ ] Create a `SettingsDatabase` class.
    - [ ] Refactor `SettingsViewModel` to use the Room database for all non-sensitive settings.
- [ ] **8.3: Final Testing and Documentation Update**
    - [ ] Write E2E tests for the new features using UI Automator as described in `testing.md`.
    - [ ] Update all documentation to reflect the final, implemented state of the application.