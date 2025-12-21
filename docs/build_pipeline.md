# **The "No-Gradle" On-Device Build Pipeline**

To achieve the necessary speed and low resource footprint for on-device compilation, IDEaz eschews a full Gradle system in favor of a direct, scripted orchestration of core command-line build tools, managed entirely by the `On-Device Build Service`.

This "No-Gradle" approach prioritizes speed, simplicity, and a minimal resource footprint.

## **1. Toolchain Components**

The necessary build tools are not part of the standard Android OS and must be bundled within the IDEaz application package.

### **1.1 Bundling Strategy**

The required command-line tools are **static, native `aarch64` binaries** (e.g., `aapt2`, `kotlinc`, `d8`, `apksigner`). They are not extracted from assets, as the `assets` directory is mounted `noexec`.

Instead, they are placed directly in the **`app/src/main/jniLibs/arm64-v8a/`** directory and **renamed to the `lib*.so` format** (e.g., `aapt2` becomes `libaapt2.so`). The `app/build.gradle.kts` file is configured with a `sourceSets` block to explicitly include this `jniLibs` directory.

This forces the Android Gradle Plugin to package them into the APK's native library directory (`lib/arm64-v8a/`). At runtime, the `ToolManager` can get a direct, executable path to these tools from the `ApplicationInfo.nativeLibraryDir`, which is **not** mounted as `noexec`.

Data-only files, such as `android.jar` and `debug.keystore`, are placed in `app/src/main/assets/` and are extracted to internal storage by the `ToolManager`.

### **1.2 Core Tools**

The build pipeline relies on the following essential native binaries and data files:

#### **Table 1: On-Device Build Toolchain Components**

| Tool | Type | Function | Origin / Notes |
| :--- | :--- | :--- | :--- |
| **aapt2** | Native | Compiles/links resources. | Static `aarch64` binary. Bundled as `libaapt2.so`. |
| **kotlinc** | Native | Compiles Kotlin/Java source. | Static `aarch64` binary. Bundled as `libkotlinc.so`. |
| **d8** | Native | Converts .class to .dex. | Static `aarch64` binary. Bundled as `libd8.so`. |
| **apksigner** | Native | Signs the final APK. | Static `aarch64` binary. Bundled as `libapksigner.so`. |
| **java** | Native | Java runtime for `kotlinc`. | Static `aarch64` binary (JDK 17). Bundled as `libjava.so`. |
| **jules** | Native | AI agent CLI (Legacy). | Node.js runtime. Bundled as `libjules.so`. **Currently Bypassed in favor of API.** |
| **gemini** | Native | AI agent CLI. | Gemini CLI. Bundled as `libgemini.so`. |
| **android.jar** | Asset | Android platform API stubs. | Data-only file from Android SDK (e.g., API 36). Bundled in `assets/`. |
| **debug.keystore**| Asset | Debug signing key. | Data-only file. Bundled in `assets/`. |

### **1.3 Toolchain Validation and Repair**
The `ToolManager` performs a validation check on initialization. It verifies that asset-based tools (like `android.jar`) exist in the internal storage **and** are not empty (0 bytes). If a corrupt or missing file is detected, it automatically attempts to re-extract it from the APK assets. Ideally, build steps also validate their inputs before execution to provide clear error messages.

## **2. The Build Sequence**

### **Pre-Build Phase: Version Control**

Before the build pipeline is invoked, the IDEaz host application ensures the project source code is up-to-date.

*   **Git Pull:** When the user initiates a build, the application uses the `GitManager` to pull the latest changes.
*   **AI Patch Application:** If the AI agent has generated a code patch, it is applied locally before triggering a rebuild.

### **Build Execution Steps**

The `On-Device Build Service` executes the following precise sequence of command-line invocations. This order is critical for correctness.

1.  **Step 1: Resource Compilation (aapt2 compile):** The service invokes `libaapt2.so compile`, passing it the `res/` directory.
2.  **Step 2: Resource Linking (aapt2 link):** The service invokes `libaapt2.so link`.
    *   **Validation:** Before execution, this step explicitly verifies the integrity of the `android.jar` file to prevent "Invalid file" errors.
    *   **Output:** Generates `resources.apk` and `R.java`.
3.  **Step 3: Source Code Compilation (kotlinc):** The service invokes the **`libkotlinc.so`** binary, compiling user code and the generated `R.java` against `android.jar`.
4.  **Step 4: Dexing (d8):** The service uses **`libd8.so`** to convert `.class` files into `classes.dex`.
5.  **Step 5: Final APK Packaging:** The `resources.apk` and `classes.dex` are zipped together into an unsigned APK.
6.  **Step 6: Signing (apksigner):** The service invokes **`libapksigner.so`** to sign the APK with the debug keystore.
7.  **Step 7: Source Map Generation:** *Only after a successful build*, the service runs `GenerateSourceMap`. This parses the `res/layout/` directory to map `android:id` attributes to line numbers for the UI Inspection Service.
8.  **Step 8: Installation:** The service uses `ApkInstaller` to install the APK.
    *   **Auto-Launch:** The `MainActivity` listens for the `ACTION_PACKAGE_REPLACED` broadcast and automatically launches the app once installation is complete.

## **3. On-Device Dependency Resolution**

Replicating Gradle's dependency resolution is a significant challenge. IDEaz addresses this with a hybrid, managed approach.

1.  **Bundled Core Libraries:** IDEaz ships with a pre-packaged set of the most common AndroidX and Material Design libraries (in `ProcessAars`).
2.  **Simplified Dependency Declaration:** Users declare dependencies in a simple `dependencies.toml` file.
3.  **On-Device Maven Resolver:** The Build Service includes a lightweight Maven artifact resolver (`HttpDependencyResolver`) to download dependencies from Maven Central/Google Maven/JitPack to a local cache.
