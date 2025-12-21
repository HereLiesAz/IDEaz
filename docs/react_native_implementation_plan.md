# React Native Implementation Plan (Partial/Stalled)

**Status:** Implementation is currently stalled. Basic bundler logic exists (`SimpleJsBundler`) but is unused. The focus is on Native Android and Web first.

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
*   **Task:** Needs a specific `ReactNativeBuildStep` in `BuildOrchestrator`.
*   **Dependencies:** Needs `node` and `npm`/`yarn` binaries (Missing on device).
    *   *Alternative:* Use the internal bundler (limited compatibility).

## 4. Platform Decisions (From previous roadmap)
*   **Bridge:** Use a custom Java-JS bridge (e.g. `WebViewJavascriptBridge`) or standard RN Native Modules.
*   **Layout:** Needs a way to map standard RN views to `AccessibilityNodeInfo` for the overlay.
*   **Hot Reload:** Initially just full reload. HMR is too complex for on-device implementation v1.

## 5. Next Steps (When Resumed)
1.  Complete `SimpleJsBundler` unit tests.
2.  Implement `ReactNativeBuildStep` in `BuildOrchestrator`.
3.  Create a sample React Native project template in `assets/templates/react_native`.
4.  Verify `AndroidProjectHost` can load the bundled assets.
