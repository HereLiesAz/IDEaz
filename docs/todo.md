# IDEaz: Phased Implementation Roadmap

This document outlines the practical, phased implementation plan for constructing the IDEaz.

---

### **Phase 5: Refinement and Advanced Features**
The final phase transforms the functional prototype into a polished, performant, and feature-rich application.

- [x] **5.1: Implement Incremental Builds**
- [x] **5.2: Implement On-Device Dependency Resolution**
- [x] **5.3: Implement the AI Debugger** (For user project errors)
- [x] **5.4: Implement Robust Error Handling**
    - [x] **Auto-Launch:** App launches automatically after build/install.
    - [x] **Automated Bug Reporting:** Internal IDE errors are reported to GitHub via API.
    - [x] **Toolchain Recovery:** Corrupt/Missing assets are auto-repaired.
    - [x] **Build Pipeline Ordering:** Fixed execution order (SourceMap last).
- [x] **5.5: Polish and Test (Theming, UI/UX Review, Performance)**

---

### **Phase 6: Advanced UI/UX and Background Operation Enhancements**
This phase focuses on improving the user experience during long-running background tasks.

- [x] **6.1: Implement Live Output Bottom Card**
- [x] **6.2: Implement Contextual AI Overlay UI**
    - [x] Refine interaction: Select/Interact modes, Drag/Tap selection, Inline Chat.
- [x] **6.3: Implement Persistent Status Notification**

---

### **Phase 7: Project Templates and Multi-Platform Support**
This phase will broaden the IDE's appeal and capabilities.

- [x] **7.1: Lay Groundwork for Multi-Platform Support**
- [x] **7.2: Implement Web Design Support**
- [ ] **7.3: Implement React Native Support** (In Progress - Pending JS Bundler refinement)
- [ ] **7.4: Implement Flutter Support** (Templates Added)

---

### **Phase 8: Build System Overhaul Investigation**

---

### **Phase 9: Enhanced Developer Tooling**
This phase introduces more traditional IDE features, inspired by CodeOps-Studio, to provide developers with more control and insight into their projects.

- [x] **9.1: Implement Read-Only File Explorer**
- [x] **9.2: Implement Git Integration Screen**
- [x] **9.3: Refactor Logging UI**
- [x] **9.4: Implement Dependencies Screen**
