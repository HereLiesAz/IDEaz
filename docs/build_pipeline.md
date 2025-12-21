# **The "No-Gradle" On-Device Build Pipeline**

To achieve the necessary speed and low resource footprint for on-device compilation, IDEaz eschews a full Gradle system in favor of a direct, scripted orchestration of core command-line build tools, managed entirely by the `On-Device Build Service`.

This "No-Gradle" approach prioritizes speed, simplicity, and a minimal resource footprint.

## **1. Toolchain Components**

The necessary build tools are not part of the standard Android OS and must be downloaded and managed by the IDEaz application.

### **1.1 Distribution Strategy**

The build tools are distributed as a **downloadable zip archive** (e.g., `tools.zip`). The `ToolManager` downloads and extracts this archive to the application's internal storage (`filesDir/local_build_tools/`).

This approach avoids bundling large binaries in the APK and allows for toolchain updates without an app update. The directory structure typically includes:
*   `tools/`: JAR files (`kotlin-compiler.jar`, `d8.jar`, `android.jar`).
*   `native/`: Native executables (`libaapt2.so`, `libjdk.so`).

### **1.2 Core Tools**

The build pipeline relies on the following essential components:

#### **Table 1: On-Device Build Toolchain Components**

| Tool | Type | Function | Execution Method |
| :--- | :--- | :--- | :--- |
| **aapt2** | Native | Compiles/links resources. | Executed directly (`libaapt2.so`). |
| **kotlinc** | JAR | Compiles Kotlin/Java source. | Executed via Java (`java -jar kotlin-compiler.jar`). |
| **d8** | JAR | Converts .class to .dex. | Executed via Java (`java -jar d8.jar`). |
| **apksigner** | JAR | Signs the final APK. | Executed via Java (`java -jar apksigner.jar`). |
| **java** | Native | Java Runtime (JDK 17). | Executed directly (`libjdk.so` / `java`). Used to run JAR tools. |
| **android.jar** | Data | Android platform API stubs. | Classpath argument. |
| **debug.keystore**| Data | Debug signing key. | Argument to apksigner. |

### **1.3 Toolchain Validation and Repair**
The `ToolManager` performs a validation check on initialization. It verifies that critical files exist in the `local_build_tools` directory. If tools are missing, the user is prompted to download them (or they are downloaded automatically if configured).

## **2. The Build Sequence**

### **Pre-Build Phase: Version Control**

Before the build pipeline is invoked, the IDEaz host application ensures the project source code is up-to-date.

*   **Git Pull:** When the user initiates a build, the application uses the `GitManager` to pull the latest changes.
*   **AI Patch Application:** If the AI agent has generated a code patch, it is applied locally before triggering a rebuild.

### **Build Execution Steps**

The `On-Device Build Service` executes the following precise sequence.

1.  **Step 1: Dependency Resolution:** `HttpDependencyResolver` downloads dependencies to `local-repo`.
2.  **Step 2: Resource Processing:** `ProcessAars` extracts resources from AAR dependencies.
3.  **Step 3: Hybrid Host Generation (Optional):** If a Redwood Schema is detected, `RedwoodCodegen` generates Host bindings.
4.  **Step 4: Resource Compilation (aapt2 compile):** Invokes `libaapt2.so compile` on `res/`.
5.  **Step 5: Resource Linking (aapt2 link):** Invokes `libaapt2.so link` to generate `R.java` and `resources.apk`.
6.  **Step 6: Source Code Compilation (kotlinc):** Invokes `kotlin-compiler.jar` to compile user code + `R.java` + Host bindings.
7.  **Step 7: Dexing (d8):** Invokes `d8.jar` to convert classes to DEX.
8.  **Step 8: APK Packaging:** Zips `resources.apk` and `classes.dex` into an unsigned APK.
9.  **Step 9: Signing (apksigner):** Invokes `apksigner.jar` with the debug keystore.
10. **Step 10: Guest Code Compilation (Optional):** If Hybrid Host is active:
    *   `RedwoodCodegen` generates Guest bindings.
    *   `ZiplineCompile` compiles Guest code to JS.
    *   `ZiplineManifestStep` generates signed manifest.
11. **Step 11: Source Map Generation:** Runs `GenerateSourceMap` to map UI IDs to code lines.
12. **Step 12: Installation:** Uses `ApkInstaller` to install the signed APK.

## **3. On-Device Dependency Resolution**

Replicating Gradle's dependency resolution is a significant challenge. IDEaz addresses this with a hybrid, managed approach.

1.  **Simplified Dependency Declaration:** Users declare dependencies in a simple `dependencies.toml` file.
2.  **On-Device Maven Resolver:** The Build Service includes a lightweight Maven artifact resolver (`HttpDependencyResolver`) to download dependencies from Maven Central/Google Maven/JitPack to a local cache.
