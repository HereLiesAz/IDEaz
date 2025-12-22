# IDEaz: Python Implementation Checklist

This document details the step-by-step plan to implement Python support in IDEaz, enabling a "Post-Code" development environment with Server-Driven UI (SDUI).

## Phase 1: Toolchain Preparation (The PythonInjector)
- [x] **1.1: Dependency Configuration**
    - [x] Add `com.chaquo.python:runtime` dependency to `gradle/libs.versions.toml`.
    - [x] Update `HttpDependencyResolver` to support extracting specific paths (`jni/`, `assets/`) from AARs.
- [x] **1.2: Build Step Implementation**
    - [x] Create `PythonInjector` build step in `BuildOrchestrator`.
    - [x] Implement logic to extract `libpython*.so` and `libchaquopy_java.so` from the runtime AAR.
    - [x] Implement logic to copy `.so` files to `app/build/intermediates/jniLibs/{abi}/`.
    - [x] Implement logic to extract `assets/` (stdlib) from AAR and merge into `app/src/main/assets/python/`.
    - [x] Extract `classes.jar` from AAR and add to `KotlincCompile` classpath and `D8Compile` inputs. (Handled by existing `ProcessAars` logic).
- [x] **1.3: Manifest Injection**
    - [x] Update `ProcessManifest` step to inject `android:extractNativeLibs="true"` into the `<application>` tag.
    - [x] Inject `android.permission.INTERNET` if not present (required for local SDUI server).
    - [x] Inject `android.permission.FOREGROUND_SERVICE` (if moving to a service-based host).

## Phase 2: The Runtime Bootstrap
- [x] **2.1: Bootstrap Logic**
    - [x] Create `PythonBootstrapper.kt` (or similar utility) in the project templates.
    - [x] Implement `initialize(context: Context)` function.
    - [x] Implement logic to check if `filesDir/python` exists; if not, extract `assets/python` to `filesDir`.
    - [x] Set `PYTHONHOME` environment variable to `filesDir/python`. (Handled by AndroidPlatform/bootstrap).
    - [x] Call `System.loadLibrary("chaquopy_java")` (and `libpython` if needed).
    - [x] Initialize `com.chaquo.python.Python` with `AndroidPlatform`.

## Phase 3: The SDUI Template & Runtime
- [x] **3.1: Python Project Template**
    - [x] Create `app/src/main/assets/templates/python/` directory.
    - [x] Create `main.py`: Entry point with a basic HTTP server (Flask/FastAPI/http.server) binding to localhost.
    - [x] Create `ui_schema.py`: Helper classes/functions to generate the JSON UI schema.
- [x] **3.2: Android Host Template**
    - [x] Create `MainActivity.kt` for the Python template.
    - [x] Implement `PythonService` (Foreground Service) to host the Python process/server.
    - [x] Implement `DynamicUiRenderer` (Composable) to consume JSON and render UI.
    - [x] Implement `UiState` and `ViewModel` to poll/stream data from the local Python server.

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
