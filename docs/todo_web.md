# Web Implementation Checklist

This document details the step-by-step plan for completing the Web support in IDEaz.
Objective: Enable users to create, edit, preview, and deploy static web applications (HTML/CSS/JS).

## Phase 1: Project Initialization & Templates
- [x] **1.1: Template Refinement**
    - [x] Review existing `app/src/main/assets/templates/web/`.
    - [x] Ensure `index.html`, `style.css`, `script.js` are present and linked.
    - [x] Add `manifest.json` for PWA support (optional but recommended).
- [x] **1.2: Project Detection**
    - [x] Verify `ProjectAnalyzer.kt` correctly identifies Web projects (via `index.html`).
    - [x] Verify `ProjectType.WEB` assignment.

## Phase 2: Runtime (WebProjectHost)
- [x] **2.1: WebView Configuration**
    - [x] Audit `WebProjectHost` WebView settings (JS enabled, DOM storage, File access).
    - [x] Ensure `WebChromeClient` and `WebViewClient` are correctly set up.
- [x] **2.2: Console Log Bridging**
    - [x] Implement `ConsoleMessage` interception in `WebChromeClient.onConsoleMessage`.
    - [x] Forward logs to the IDEaz "Build/Logs" bottom sheet.
    - [x] Prefix logs with `[WEB]` for clarity.
- [x] **2.3: Error Handling**
    - [x] Catch page load errors (404, etc.) and display a user-friendly error page or Toast.

## Phase 3: Build System (Local)
*For Web, "Build" implies validation and potentially minification.*
- [x] **3.1: Local "Build"**
    - [x] Implement `WebBuildStep` in `BuildService`.
    - [x] Step 1: Validate HTML syntax (basic check).
    - [x] Step 2: (Optional) Simple minification of JS/CSS using Kotlin-based regex or logic (no heavy tools).
    - [x] Step 3: Copy files to `dist/` or prepare for deployment.

## Phase 4: Remote Build & Deployment
- [ ] **4.1: GitHub Pages Integration**
    - [ ] Create `web_ci_pages.yml` in assets.
    - [ ] Configure it to deploy the project root (or `dist/`) to `gh-pages` branch.
    - [ ] Auto-enable GitHub Pages via API (if possible) or instruct user.
- [ ] **4.2: Git Integration**
    - [ ] Ensure `GitDelegate` pushes changes correctly.
    - [ ] Add "Deploy" action in UI that triggers the GitHub Pages workflow.

## Phase 5: Editor Features
- [ ] **5.1: Live Preview**
    - [ ] Implement "Live Reload" logic:
        - [ ] Watch file changes in `filesDir`.
        - [ ] Trigger `webView.reload()` automatically on save.
- [ ] **5.2: Syntax Highlighting**
    - [ ] Update Editor/Log viewer to support HTML/CSS/JS syntax.

## Phase 6: UI & Overlay Integration
- [ ] **6.1: Overlay Support**
    - [ ] Web projects run in `WebProjectHost` which is already part of `MainScreen`.
    - [ ] Ensure the IDEaz overlay (Select Mode) works over the WebView.
        - [ ] WebView accessibility node mapping might be complex.
        - [ ] Verify "Drag to Select" can identify HTML elements (via AccessibilityNodeInfo).
