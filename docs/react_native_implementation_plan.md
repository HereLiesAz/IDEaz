# React Native Implementation Plan (Partial/Stalled)

**Status:** Implementation is currently in progress. Bundler logic is integrated. Runtime shim (WebView-based) supports basic components and Native Modules.

## 1. Bundling Strategy
*   **Tool:** `SimpleJsBundler` (Custom Kotlin implementation).
*   **Logic:**
    *   Parse `app.json` to find entry point.
    *   Regex replacement to inject `AppRegistry`.
    *   Copy assets from project to build output.
*   **Limitations:** Does not support full Metro features (HMR, complex resolution). No JSX compilation (requires `React.createElement` or Babel).

## 2. Runtime
*   **Host:** `AndroidProjectHost` (Virtual Display) running the compiled APK.
*   **Implementation:** `MainActivity` uses `WebView` to load `index.html`.
*   **Shim:** `rn-shim.js` implements:
    *   `View`, `Text`, `Image`, `TextInput`, `Button`, `ScrollView`, `TouchableOpacity`, `Alert`.
    *   `StyleSheet` (basic pass-through).
    *   `AppRegistry`.
*   **Native Modules:** Implemented via `AndroidBridge` (`@JavascriptInterface`) in `MainActivity`. Exposed as `NativeModules` in JS (e.g. `ToastAndroid`).

## 3. Build Service Integration
*   **Task:** `ReactNativeBuildStep` is integrated into `BuildService`.
*   **Dependencies:** Uses the internal bundler (no node/npm required).

## 4. Platform Decisions (From previous roadmap)
*   **Bridge:** Use a custom Java-JS bridge (`WebView.addJavascriptInterface`).
*   **Layout:** Web-based layout (DOM/Flexbox) inside WebView.
*   **Hot Reload:** Initially just full reload.

## 5. Completed Steps
- [x] Complete `SimpleJsBundler` unit tests.
- [x] Implement `ReactNativeBuildStep` in `BuildOrchestrator` (via `BuildService`).
- [x] Create a sample React Native project template in `assets/templates/react_native`.
- [x] Implement React Native Runtime (WebView Shim in template).
- [x] Update `ReactNativeBuildStep` to copy assets.
- [x] Expand `rn-shim.js` with more components.
- [x] Implement Native Modules support.

## 6. Next Steps
1.  Verify `AndroidProjectHost` can load the bundled assets (requires running the app).
2.  Add JSX compilation support (Babel Standalone or regex transform).
3.  Add support for more complex components (FlatList, SectionList).
4.  Implement navigation support.
