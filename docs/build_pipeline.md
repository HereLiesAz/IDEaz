# **The "No-Gradle" On-Device Build Pipeline**

To achieve the necessary speed and low resource footprint for on-device compilation, IDEaz eschews a full Gradle system in favor of a direct, scripted orchestration of core command-line build tools, managed entirely by the `On-Device Build Service`.

This "No-Gradle" approach prioritizes speed, simplicity, and a minimal resource footprint.

## **1. Toolchain Components**

The necessary build tools are not part of the standard Android OS and must be bundled within the IDEaz application package.

### **1.1 Bundling Strategy**

The required command-line tools are **static, native `aarch64` binaries** (e.g., `aapt2`, `kotlinc`, `d8`, `apksigner`). They are not extracted from assets, as the `assets` directory is mounted `noexec`.

Instead, they are placed directly in the **`app/src/main/jniLibs/arm64-v8a/`** directory and **renamed to the `lib*.so` format** (e.g., `aapt2` becomes `libaapt2.so`). The `app/build.gradle.kts` file is configured with a `sourceSets` block to explicitly include this `jniLibs` directory.

This forces the Android Gradle Plugin to package them into the APK's native library directory (`lib/arm64-v8a/`). At runtime, the `ToolManager` can get a direct, executable path to these tools from the `ApplicationInfo.nativeLibraryDir`, which is **not** mounted as `noexec`. This bypasses all issues with file extraction and permissions.

Data-only files, such as `android.jar` and `debug.keystore`, are correctly placed in `app/src/main/assets/` and are extracted to internal storage by the `ToolManager`.

### **1.2 Core Tools**

The build pipeline relies on the following essential native binaries and data files:

#### **Table 1: On-Device Build Toolchain Components**

| Tool | Type | Function | Origin / Notes |
| :--- | :--- | :--- | :--- |
| **aapt2** | Native | Compiles/links resources. | Static `aarch64` binary from `lzhiyong/android-sdk-tools`. Bundled as `libaapt2.so`. |
| **kotlinc** | Native | Compiles Kotlin/Java source. | Static `aarch64` binary from `lzhiyong/android-sdk-tools`. Bundled as `libkotlinc.so`. |
| **d8** | Native | Converts .class to .dex. | Static `aarch64` binary from `lzhiyong/android-sdk-tools`. Bundled as `libd8.so`. |
| **apksigner** | Native | Signs the final APK. | Static `aarch64` binary from `lzhiyong/android-sdk-tools`. Bundled as `libapksigner.so`. |
| **java** | Native | Java runtime for `kotlinc`. | Static `aarch64` binary from Termux `openjdk-17`. Bundled as `libjava.so`. |
| **jules** | Native | AI agent CLI. | Custom-compiled Node.js package. Bundled as `libjules.so`. |
| **android.jar** | Asset | Android platform API stubs. | Data-only file from Android SDK (e.g., API 36). Bundled in `assets/`. |
| **debug.keystore**| Asset | Debug signing key. | Data-only file. Bundled in `assets/`. |

## **2. The Build Sequence**

The `On-Device Build Service` executes the following precise sequence of command-line invocations to transform a project's source code into a runnable APK.

1.  **Step 1: Resource Compilation (aapt2 compile):** The service invokes `libaapt2.so compile`, passing it the `res/` directory. This command does *not* take SDK version flags.
2.  **Step 2: Resource Linking (aapt2 link):** The service invokes `libaapt2.so link`. This command takes the compiled `.flat` files, the `AndroidManifest.xml`, a reference to the platform's `android.jar` (via `-I`), and the **`--minsdk`** and **`--targetsdk`** flags. This generates a preliminary `resources.apk` and the `R.java` file.
    * **Source Map Generation:** During this step, the service also runs a custom XML parser over the `res/layout/` directory. It extracts every `android:id="@+id/..."` attribute, its file path, and its line number, writing this data to the `source_map.json` file.
3.  **Step 3: Source Code Compilation (kotlinc):** The service invokes the **`libkotlinc.so`** binary, providing a classpath that includes the platform `android.jar`, any library dependencies, and the source paths for both the user's code and the generated `R.java`.
4.  **Step 4: Dexing (d8):** The service then uses the **`libd8.so`** binary to convert all the generated `.class` files into one or more `classes.dex` files.
5.  **Step 5: Final APK Packaging:** The final APK is a zip archive. The service creates this archive by combining the `resources.apk` from Step 2 with the `classes.dex` file(s) from Step 4.
6.  **Step 6: Signing (apksigner):** The service invokes the **`libapksigner.so`** binary's `sign` command, providing the bundled `debug.keystore` and the path to the APK.
7.  **Step 7: Installation:** The service notifies the Host App of the final APK path, which then creates an `Intent` to present a system prompt to the user to install or update the application.

## **3. On-Device Dependency Resolution**

Replicating Gradle's dependency resolution is a significant challenge. IDEaz addresses this with a hybrid, managed approach.

1.  **Bundled Core Libraries:** IDEaz ships with a pre-packaged set of the most common AndroidX and Material Design libraries.
2.  **Simplified Dependency Declaration:** Users declare dependencies in a simple `dependencies.toml` file.
3.  **On-Device Maven Resolver:** The Build Service includes a lightweight Maven artifact resolver. When a build is initiated, this resolver will:
    * Parse the `dependencies.toml` file.
    * Check its local cache for each dependency.
    * If a dependency is not found, it will construct the appropriate URL for Maven Central and download the required `.aar` or `.jar` file directly to local storage.

## **4. Incremental Build System**

To achieve near-instant rebuilds, the Build Service implements an incremental build system by storing and comparing file checksums (e.g., SHA-256 hashes) of all source and resource files.

* If only a single Kotlin source file has changed, the pipeline can intelligently skip resource compilation and linking (Steps 1 & 2).
* If a resource file changes, the pipeline must re-run from Step 1, but it can still reuse cached outputs for unchanged resources.
* If the `dependencies.toml` file changes, a full dependency resolution and a clean build are required.