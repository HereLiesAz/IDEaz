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
- [x] **4.1: GitHub Pages Integration**
    - [x] Ensure `web_ci_pages.yml` is injected (via `ProjectConfigManager` code).
    - [x] Configure it to deploy the project root (or `dist/`) to `gh-pages` branch.
    - [x] Auto-enable GitHub Pages via API (if possible) or instruct user.
- [x] **4.2: Git Integration**
    - [x] Ensure `GitDelegate` pushes changes correctly.
    - [x] Add "Deploy" action in UI that triggers the GitHub Pages workflow.

## Phase 5: Editor Features
- [x] **5.1: Live Preview**
    - [x] Implement "Live Reload" logic:
        - [x] Watch file changes in `filesDir` (Root directory only for MVP).
        - [x] Trigger `webView.reload()` automatically on save.
- [x] 5.2: Syntax Highlighting
    - [x] Update Editor (Integrated Sora Editor in FileContentScreen).

## Phase 6: UI & Overlay Integration
- [x] **6.1: Overlay Support**
    - [x] Web projects run in `WebProjectHost` which is already part of `MainScreen`.
    - [x] Ensure the IDEaz overlay (Select Mode) works over the WebView (Implemented via SelectionOverlay and JS inspection).
        - [x] WebView accessibility node mapping might be complex (Bypassed via JS injection).
        - [x] Verify "Drag to Select" can identify HTML elements (Implemented `INSPECT_WEB` broadcast).
