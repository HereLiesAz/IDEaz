# IDEaz: Granular Implementation Checklist

This document is the step-by-step guide for taking IDEaz from concept to production.

## Phase 1: Foundation & Infrastructure
- [ ] **1.1: Project Structure Setup**
    - [x] Define multi-process architecture (`:app`, `:build_process`, `:inspection_service`).
    - [x] Create `BuildService` (Foreground Service).
    - [x] Create `UIInspectionService` (Accessibility Service).
- [ ] **1.2: Data Layer**
    - [x] Implement `GitManager` (JGit wrapper).
    - [x] Implement `SettingsViewModel` (SharedPreferences).
    - [x] Implement `ProjectAnalyzer` for project type detection.
    - [x] Implement auto-discovery of local projects and import functionality.
- [ ] **1.3: "Race to Build" Logic**
    - [x] **1.3.1: Artifact Detection:** Implement logic to compare Installed SHA vs Remote Release SHA vs Repo Head SHA. (Implemented manual check for updates).
    - [ ] **1.3.2: Remote Polling:** Implement loop to check GitHub Releases for new builds.
    - [ ] **1.3.3: Local Build:** Implement background build thread with lower priority.
    - [ ] **1.3.4: Cancellation:** Implement logic to cancel local build if remote wins (and vice versa).

## Phase 2: The Build Pipeline ("No-Gradle" on Device)
- [ ] **2.1: Toolchain Management**
    - [x] `ToolManager` to extract `aapt2`, `d8`, `kotlinc`, `java`.
- [ ] **2.2: Build Steps**
    - [x] `ProcessManifest`
    - [x] `ProcessAars` (Extract & Compile Resources)
    - [x] `Aapt2Compile` & `Aapt2Link`
    - [x] `KotlincCompile`
    - [x] `D8Compile`
    - [x] `ApkSign`
- [ ] **2.3: Dependency Resolution**
    - [x] `HttpDependencyResolver` (Maven/Aether integration).
    - [ ] **Refinement:** Handle complex POMs and exclusions robustly.

## Phase 3: UI/UX & Interaction
- [ ] **3.1: The Overlay**
    - [x] **3.1.1: Attachment:** Implement Bubble Notification for persistent overlay.
    - [ ] **3.1.2: Transparency:** Ensure transparent background in IDE mode, Opaque in Settings.
    - [ ] **3.1.3: Selection:** Tap (Node) and Drag (Rect) selection logic.
- [ ] **3.2: The Console**
    - [x] Bottom Sheet implementation.
    - [ ] **3.2.1: Live Logs:** Stream Logcat/Build logs to the sheet.
    - [ ] **3.2.2: Persistent Notification:** Show last 3 log lines in notification.
- [ ] **3.3: Feedback Loops**
    - [ ] **3.3.1: Update Popup:** "Updating, gimme a sec" dialog.
    - [ ] **3.3.2: Clipboard:** Auto-copy prompt text on update.

## Phase 4: AI Integration & Workflow
- [ ] **4.1: Jules Integration**
    - [x] `JulesApiClient`.
    - [ ] **4.1.1: Session Management:** Create/Delete/Resume sessions.
    - [ ] **4.1.2: Polling:** Implement infinite polling for *activities* (not just patch).
- [ ] **4.2: Workflow Injection (Initialization)**
    - [ ] **4.2.1: File Creation:** Generate `android_ci_jules.yml`, `codeql.yml`, `jules.yml`, `release.yml`.
    - [ ] **4.2.2: Force Push:** Logic to commit and push these files on "Save & Initialize".
- [ ] **4.3: Error Handling Loop**
    - [ ] **4.3.1: User Error:** If build fails (compilation), send log to Jules.
    - [ ] **4.3.2: IDE Error:** If build crashes (exception), report to `HereLiesAz/IDEaz` with label `jules`.

## Phase 5: Production Polish
- [ ] **5.1: Multi-Platform Support**
    - [ ] Web Support (Implemented).
    - [ ] React Native Support (In Progress).
    - [ ] Flutter Support (Planned).
- [ ] **5.2: Testing**
    - [ ] Unit Tests for all ViewModels.
    - [ ] Integration Tests for Build Pipeline.

## Phase 6: Maintenance
- [ ] Keep `docs/` up to date.
- [ ] Monitor GitHub Issues reported by the IDE.
