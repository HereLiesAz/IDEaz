# IDEaz: Python Implementation Checklist

This document details the step-by-step plan to implement Python support in IDEaz, enabling a "Post-Code" development environment with Server-Driven UI (SDUI).

## Phase 1: Toolchain Preparation (The PythonInjector)
- [ ] **1.1: Dependency Configuration**
    - [ ] Add `com.chaquo.python:runtime` dependency to `gradle/libs.versions.toml`.
    - [ ] Update `HttpDependencyResolver` to support extracting specific paths (`jni/`, `assets/`) from AARs.
- [ ] **1.2: Build Step Implementation**
    - [ ] Create `PythonInjector` build step in `BuildOrchestrator`.
    - [ ] Implement logic to extract `libpython*.so` and `libchaquopy_java.so` from the runtime AAR.
    - [ ] Implement logic to copy `.so` files to `app/build/intermediates/jniLibs/{abi}/`.
    - [ ] Implement logic to extract `assets/` (stdlib) from AAR and merge into `app/src/main/assets/python/`.
    - [ ] Extract `classes.jar` from AAR and add to `KotlincCompile` classpath and `D8Compile` inputs.
- [ ] **1.3: Manifest Injection**
    - [ ] Update `ProcessManifest` step to inject `android:extractNativeLibs="true"` into the `<application>` tag.
    - [ ] Inject `android.permission.INTERNET` if not present (required for local SDUI server).
    - [ ] Inject `android.permission.FOREGROUND_SERVICE` (if moving to a service-based host).

## Phase 2: The Runtime Bootstrap
- [ ] **2.1: Bootstrap Logic**
    - [ ] Create `PythonBootstrapper.kt` (or similar utility) in the project templates.
    - [ ] Implement `initialize(context: Context)` function.
    - [ ] Implement logic to check if `filesDir/python` exists; if not, extract `assets/python` to `filesDir`.
    - [ ] Set `PYTHONHOME` environment variable to `filesDir/python`.
    - [ ] Call `System.loadLibrary("chaquopy_java")` (and `libpython` if needed).
    - [ ] Initialize `com.chaquo.python.Python` with `AndroidPlatform`.

## Phase 3: The SDUI Template & Runtime
- [ ] **3.1: Python Project Template**
    - [ ] Create `app/src/main/assets/templates/python/` directory.
    - [ ] Create `main.py`: Entry point with a basic HTTP server (Flask/FastAPI/http.server) binding to localhost.
    - [ ] Create `ui_schema.py`: Helper classes/functions to generate the JSON UI schema.
- [ ] **3.2: Android Host Template**
    - [ ] Create `MainActivity.kt` for the Python template.
    - [ ] Implement `PythonService` (Foreground Service) to host the Python process/server.
    - [ ] Implement `DynamicUiRenderer` (Composable) to consume JSON and render UI.
    - [ ] Implement `UiState` and `ViewModel` to poll/stream data from the local Python server.

## Phase 4: Hot Reload Implementation
- [ ] **4.1: File Watching**
    - [ ] Update `BuildService` or `IdeazOverlayService` to monitor `src/python` changes in the user project.
    - [ ] Implement logic to copy modified files to `filesDir/python` immediately.
- [ ] **4.2: Reload Signal**
    - [ ] Define broadcast intent `com.ideaz.ACTION_RELOAD_PYTHON`.
    - [ ] Implement `BroadcastReceiver` in `PythonService` to catch the intent.
    - [ ] Implement Python logic (e.g., `importlib.reload`) to reload the module and restart the server/state without killing the process.

## Phase 5: Verification & Testing
- [ ] **5.1: Build Verification**
    - [ ] Verify `PythonInjector` correctly places `.so` files in the APK.
    - [ ] Verify `classes.dex` includes Chaquopy classes.
- [ ] **5.2: Runtime Verification**
    - [ ] Verify Python initializes successfully on device.
    - [ ] Verify "Hello World" SDUI renders.
    - [ ] Verify Hot Reload updates the UI without an APK reinstall.
