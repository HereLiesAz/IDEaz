# IDEaz: Granular Implementation Checklist

This document is the step-by-step guide for taking IDEaz from concept to production.

## Phase 1: Foundation & Infrastructure
- [x] **1.1: Project Structure Setup**
    - [x] Define multi-process architecture (`:app`, `:build_process`, `:inspection_service`).
    - [x] Create `BuildService` (Foreground Service).
    - [x] Create `IdeazAccessibilityService` (Inspection) and `IdeazOverlayService` (Visual).
- [x] **1.2: Data Layer**
    - [x] Implement `GitManager` (JGit wrapper).
    - [x] Implement `SettingsViewModel` (SharedPreferences).
    - [x] Implement `ProjectAnalyzer` for project type detection.
    - [x] Implement auto-discovery of local projects and external project registration (SAF + Native File Access).
    - [x] **1.2.5: Encrypted Settings Export/Import:** Allow user to save credentials to file.
- [x] **1.3: "Race to Build" Logic**
    - [x] **1.3.1: Artifact Detection:** Implement logic to compare Installed SHA vs Remote Release SHA vs Repo Head SHA. (Implemented manual check for updates in `MainViewModel`).
    - [x] **1.3.2: Remote Polling:** Implement loop to check GitHub Releases for new builds. (Implemented in `RemoteBuildManager`).
    - [x] **1.3.3: Local Build:** Implement background build thread with lower priority.
    - [x] **1.3.4: Cancellation:** Implement logic to cancel local build if remote wins (and vice versa). (Implemented "Race to Build" in `BuildDelegate`).

## Phase 2: The Build Pipeline ("No-Gradle" on Device)
- [x] **2.1: Toolchain Management**
    - [x] `ToolManager` to extract `aapt2`, `d8`, `kotlinc`, `java`.
- [x] **2.2: Build Steps**
    - [x] `ProcessManifest`
    - [x] `ProcessAars` (Extract & Compile Resources)
    - [x] `Aapt2Compile` & `Aapt2Link`
    - [x] `KotlincCompile`
    - [x] `D8Compile`
    - [x] `ApkSign`
    - [x] **Python Injection:** `PythonInjector.kt` (Implemented).
- [x] **2.3: Dependency Resolution**
    - [x] `HttpDependencyResolver` (Maven/Aether integration).
    - [x] **Refinement:** Handle complex POMs and exclusions robustly (Added Version Catalog support).

## Phase 3: UI/UX & Interaction
- [x] **3.1: The Overlay**
    - [x] **3.1.1: Attachment:** `IdeazOverlayService` (System Alert Window) implemented and registered.
    - [x] **3.1.2: Transparency:** Transparent overlay mode functional via `OverlayView`.
    - [x] **3.1.3: Selection:** Drag selection implemented in `OverlayView` and integrated with `OverlayDelegate`.
- [x] **3.2: The Console**
    - [x] Bottom Sheet implementation.
    - [x] **3.2.1: Live Logs:** Stream Logcat/Build logs to the sheet. (Implemented `LogcatReader` and System tab).
    - [x] **3.2.2: Persistent Notification:** Show last 3 log lines in notification. (Verified `BuildService` notification logic).
- [x] **3.3: Feedback Loops**
    - [x] **3.3.1: Update Popup:** "Updating, gimme a sec" dialog. (Implemented update confirmation dialog).
    - [x] **3.3.2: Clipboard:** Auto-copy prompt text on update. (Implemented in `IdeazOverlayService`).
- [x] **3.4: UI Refinement**
    - [x] Reorder Settings Screen (Build Config first).
    - [x] Improve Project Load Tab layout.
    - [x] Enhance Accessibility in Settings (Headings, Semantics, Touch Targets).
- [x] **3.5: Dependency Management**
    - [x] UI for viewing and adding libraries via AI (`LibrariesScreen`).

## Phase 4: AI Integration & Workflow
- [x] **4.1: Jules Integration**
    - [x] `JulesApiClient` (Implemented with Sources endpoint support).
    - [x] **4.1.1: Session Management:** Create/Resume sessions (Implemented in `AIDelegate`).
    - [x] **4.1.2: Polling:** Implement infinite polling for *activities* (Implemented in `AIDelegate`).
- [x] **4.2: Workflow Injection (Initialization)**
    - [x] **4.2.1: File Creation:** Generate `android_ci_jules.yml`, `codeql.yml`, `jules.yml`, `release.yml`, `jules-issue-handler.yml`. (Updated templates to use branch-aware tags).
    - [x] **4.2.2: Force Push:** Logic to commit and push these files on "Save & Initialize".
    - [x] **4.2.3: Issue Handling:** Inject `jules-issue-handler.yml` for autonomous issue resolution.
    - [x] **4.2.4: Branch Management:** Inject `jules-branch-manager.yml` for autonomous PR lifecycle management.
    - [x] **4.2.5: Secret Consolidation:** Standardized all workflows to use `JULES_API_KEY`.
- [x] **4.3: Error Handling Loop**
    - [x] **4.3.1: User Error:** If build fails (compilation), send log to Jules.
    - [x] **4.3.2: IDE Error:** If build crashes (exception), report to `HereLiesAz/IDEaz` with label `jules`.

## Phase 5: Production Polish & Refinement
- [x] **5.1: Multi-Platform Support**
    - [x] Web Support (Runtime + Auto-Build/Correct).
    - [x] React Native Support (Bundler, Runtime, and Templates implemented).
    - [x] Flutter Support (Implemented via Remote Build, Templates available).
    - [x] Python Support (Runtime injection).
- [ ] **5.2: Testing**
    - [x] Unit Tests for SettingsViewModel.
    - [x] Unit Tests for MainViewModel (Delegates). (StateDelegate, AIDelegate)
    - [x] Integration Tests for Build Pipeline. (Implemented in `BuildPipelineTest.kt`)
- [x] **5.3: Advanced Features**
    - [x] Incremental Builds.
    - [x] On-Device Dependency Resolution.
    - [x] AI Debugger (For user project errors).
- [x] **5.4: Robust Error Handling**
    - [x] Auto-Launch: App launches automatically after build/install.
    - [x] Automated Bug Reporting: Internal IDE errors are reported to GitHub via API.
    - [x] Toolchain Recovery: Corrupt/Missing assets are auto-repaired.
    - [x] Build Pipeline Ordering: Fixed execution order (SourceMap last).

## Phase 6: Advanced UI/UX (Background Operations)
- [x] **6.1: Implement Live Output Bottom Card**
- [x] **6.2: Implement Contextual AI Overlay UI**
    - [x] Refine interaction: Select/Interact modes, Drag/Tap selection, Inline Chat. (Note: Only works within Internal Hosts).
- [x] **6.3: Implement Persistent Status Notification**
- [x] **6.4: Implement Android Virtual Environment Host** (Parity with Web workflow)

## Phase 7: Web Support
- [x] **7.1: Lay Groundwork for Multi-Platform Support**
- [x] **7.2: Implement Web Design Support**
- [x] **7.3: Remove Deprecated Platforms (React Native, Flutter)** (Note: Platforms are now fully supported. Removal cancelled).

## Phase 8: Build System Overhaul Investigation
- [x] Investigation into alternative build systems. (See Phase 11)

## Phase 9: Enhanced Developer Tooling
- [x] **9.1: Implement Read-Only File Explorer** (Implemented `FileExplorerScreen` as Escape Hatch).
- [x] **9.2: Implement Git Integration Screen**
- [x] **9.3: Refactor Logging UI**
- [x] **9.4: Implement Dependencies Screen** (Implemented `LibrariesScreen`).

## Phase 10: Maintenance
- [x] Keep `docs/` up to date.
- [ ] Monitor GitHub Issues reported by the IDE.
- [x] Migrate to `androidComponents` API in `build.gradle.kts`.
- [x] Security Hardening: Implement PBKDF2 for key derivation.
- [x] Refactor `MainViewModel` into Delegates and add KDocs.
- [x] Fix JNA native library loading (lazysodium/jna conflict).
- [x] Refactor Project Setup UI (Move APK Picker, reorder buttons).
- [x] Update Project Screen tabs order (Setup, Load, Clone).
- [x] Configure CI to sign release APKs using Repository Secrets.
- [x] **10.10: Fix Build & Test Errors:** Resolve compilation warnings and test failures (AssetExtractor, Deprecations).

## Phase 11: Hybrid Host Architecture Implementation
- [x] **11.1: Dependencies & Tooling**
    - [x] Download/Cache `redwood-tooling-codegen` JAR & dependencies via `HttpDependencyResolver`.
    - [x] Download/Cache `zipline-kotlin-plugin-embeddable` JAR (match Kotlin version).
    - [x] Download Guest runtime klibs (Redwood, Zipline, Kotlin Stdlib).
- [x] **11.2: Code Generation (Redwood)**
    - [x] Implement Host codegen invocation (`--protocol-host`, `--widget`).
    - [x] Implement Guest codegen invocation (`--protocol-guest`, `--compose`).
    - [x] Ensure source-based schema parsing (no pre-compilation of Schema.kt).
- [x] **11.3: Host Compilation (Native)**
    - [x] Update `KotlincCompile` to include generated Host code.
    - [x] Integrate `ZiplineLoader` logic into Host (`MainViewModel`).
- [x] **11.4: Guest Compilation (Zipline/JS)**
    - [x] Implement `K2JSCompiler` invocation in `BuildService`.
    - [x] Configure Zipline compiler plugin (`-Xplugin`, `-P plugin:zipline-api-validation=enabled`).
    - [x] Set up IR backend flags (`-Xir-produce-js`, `-Xir-per-module`).
- [x] **11.5: Manifest & Security**
    - [x] Implement `ZiplineManifestGenerator` (SHA-256 hashing, JSON construction).
    - [ ] Implement Ed25519 signing of manifest using `LazySodiumAndroid` (TODO).
- [x] **11.6: Hot Reload & Runtime** (BLOCKED: Zipline API Deprecation)
    - [x] Implement "Hot Reload" trigger (write manifest, broadcast `RELOAD_ZIPLINE`).
    - [x] Implement Host receiver to trigger `ziplineLoader.load`. (Disabled in `MainViewModel` due to API issues).
    - [x] Refactor `SimpleJsBundler` for Zipline module loading/bootstrapping. (Skipped: ZiplineLoader handles modular loading without bundling).
    - [x] Implement Error Handling: Capture Guest crashes and feed to Jules.

## Phase 13: Critical Fixes & Hardening (The 7 Deadly Sins)
- [x] **13.1: Thread Safety:** Fix Git operations on Main Thread.
- [x] **13.2: Recursive File Watching:** Implement `RecursiveFileObserver`.
- [x] **13.3: Security:** Sanitize crash reports (LogSanitizer).
- [x] **13.4: Dead Code:** Disable Zipline steps in BuildService.
- [x] **13.5: Configuration:** Move Build Tools repo to `BuildConfig`.
- [x] **13.6: Performance:** Optimize `AssetExtractor` loop.
- [x] **13.7: Reliability:** Use `XmlPullParser` for dependency resolution.
- [x] **13.8: Web Project Hot Reload:** Implement pipeline for Kotlin/JS web projects.

## Phase 12: Documentation & Cleanup
- [x] **12.1: Contradiction Audit**
    - [x] Verify "min-app" removal.
    - [x] Verify Project Screen Tabs order.
    - [x] Clarify Remote-Only Flutter support.
    - [x] Update `contradictions_report.md`.
