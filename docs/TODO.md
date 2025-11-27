# IDEaz: Master TODO List

This document is the central source of truth for the project's roadmap and current task status.

## Current Focus: Phase 7 - Multi-Platform Support & Phase 8 - Build System Overhaul

### **Phase 7: Project Templates and Multi-Platform Support**
- [x] **7.1: Lay Groundwork for Multi-Platform Support**
    - [x] Abstract build steps into `BuildStep` interface.
    - [x] Create `BuildOrchestrator`.
- [x] **7.2: Implement Web Design Support**
    - [x] Create `WebBuildStep`.
    - [x] Implement `HtmlSourceInjector` for source mapping.
    - [x] Create `WebRuntimeActivity`.
- [ ] **7.3: Implement React Native Support**
    - [ ] **7.3.1: JS Bundling:** Implement a robust JS bundler (Metro equivalent) for on-device execution. Currently `SimpleJsBundler` is a placeholder.
    - [ ] **7.3.2: Source Mapping:** Ensure `__source` tags are correctly injected for `UIInspectionService`.
    - [ ] **7.3.3: Native Modules:** Handle React Native AAR dependencies and native code linking.
- [ ] **7.4: Implement Flutter Support**
    - [ ] **7.4.1: Dart Compilation:** Investigate/implement on-device Dart compilation or a hot-reload compatible artifact runner.
    - [ ] **7.4.2: Inspector Integration:** Integrate with Flutter DevTools protocol or similar for UI inspection.

### **Phase 8: Build System Overhaul**
- [ ] **8.1: Optimization**
    - [ ] **8.1.1: Parallel Execution:** Run independent build steps in parallel where possible.
    - [ ] **8.1.2: Caching:** Improve `BuildCacheManager` to be more granular (file-level hashing).
- [ ] **8.2: Robustness**
    - [ ] **8.2.1: Dependency Resolution:** Upgrade `HttpDependencyResolver` to handle more complex Maven POMs and exclusions.
    - [ ] **8.2.2: Error Reporting:** Improve compiler error parsing to give user-friendly suggestions.

### **Phase 9: Enhanced Developer Tooling**
- [x] **9.1: Implement Read-Only File Explorer**
    - [x] `FileExplorerScreen.kt` implemented.
- [x] **9.2: Implement Git Integration Screen**
    - [x] `GitScreen.kt` implemented with JGit.
- [x] **9.3: Refactor Logging UI**
    - [x] `IdeBottomSheet` log display.
- [x] **9.4: Implement Dependencies Screen**
    - [x] `LibrariesScreen.kt` (Dependency management UI).

## Backlog / Maintenance

- [ ] **Docs:** Keep `screens.md` and `file_descriptions.md` up to date.
- [ ] **Tests:** Increase unit test coverage, especially for `MainViewModel` and `BuildService`.
- [ ] **UI:** Polish Material 3 implementations. Fix any "receiver type mismatch" issues in Compose.
- [ ] **Performance:** Profile memory usage of `UIInspectionService`.

## Completed Phases (Archive)

### **Phase 5: Refinement and Advanced Features**
- [x] **5.1: Implement Incremental Builds**
- [x] **5.2: Implement On-Device Dependency Resolution**
- [x] **5.3: Implement the AI Debugger**
- [x] **5.4: Implement Robust Error Handling**
- [x] **5.5: Polish and Test**

### **Phase 6: Advanced UI/UX**
- [x] **6.1: Implement Live Output Bottom Card**
- [x] **6.2: Implement Contextual AI Overlay UI**
- [x] **6.3: Implement Persistent Status Notification**
