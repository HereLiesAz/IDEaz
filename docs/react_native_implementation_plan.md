# React Native Implementation Plan (Partial/Stalled)

**Status:** Implementation is currently in progress. Basic bundler logic exists (`SimpleJsBundler`) and is integrated into the build pipeline. Runtime support (native modules) is pending.

## 1. Bundling Strategy
*   **Tool:** `SimpleJsBundler` (Custom Kotlin implementation).
*   **Logic:**
    *   Parse `app.json` to find entry point.
    *   Regex replacement to inject `AppRegistry`.
    *   Copy assets.
*   **Limitations:** Does not support full Metro features (HMR, complex resolution).

## 2. Runtime
*   **Host:** `AndroidProjectHost` (Virtual Display) running the compiled APK.
*   **Debug:** Requires bridging logs from JS to the IDE console (Not Implemented).

## 3. Build Service Integration
*   **Task:** `ReactNativeBuildStep` is integrated into `BuildService`.
*   **Dependencies:** Uses the internal bundler (no node/npm required).

## 4. Platform Decisions (From previous roadmap)
*   **Bridge:** Use a custom Java-JS bridge (e.g. `WebViewJavascriptBridge`) or standard RN Native Modules.
*   **Layout:** Needs a way to map standard RN views to `AccessibilityNodeInfo` for the overlay.
*   **Hot Reload:** Initially just full reload. HMR is too complex for on-device implementation v1.

## 5. Completed Steps
- [x] Complete `SimpleJsBundler` unit tests.
- [x] Implement `ReactNativeBuildStep` in `BuildOrchestrator` (via `BuildService`).
- [x] Create a sample React Native project template in `assets/templates/react_native`.

## 6. Next Steps
1.  Verify `AndroidProjectHost` can load the bundled assets (requires runtime implementation).
2.  Implement React Native Runtime (Java/Kotlin shim with JS engine or WebView).
3.  Add support for Native Modules.
