# **The "No-Gradle" On-Device Build Pipeline**

To achieve the necessary speed and low resource footprint for on-device compilation, IDEaz eschews a full Gradle system in favor of a direct, scripted orchestration of core command-line build tools, managed entirely by the `On-Device Build Service`.

This "No-Gradle" approach prioritizes speed, simplicity, and a minimal resource footprint.

## **1. Toolchain Components**

The necessary build tools are not part of the standard Android OS and must be bundled within the IDEaz application package.

### **1.1 Bundling Strategy**

The required command-line tools (`aapt2`, `d8`, `apksigner`) and the embeddable Kotlin compiler will be included as native binaries within the IDEaz's `assets/` or `jniLibs/` directory. Upon the first launch of the application, these binaries will be extracted to the app's private, internal storage directory (e.g., `/data/data/com.hereliesaz.ideaz/files/bin`). The `File.setExecutable(true)` method will be used to grant them the necessary execution permissions.

### **1.2 Core Tools**

The build pipeline relies on the following essential tools:

* **aapt2 (Android Asset Packaging Tool):** This tool is responsible for all resource processing. It compiles resources from the project's `res/` directory and processes the `AndroidManifest.xml`.
* **kotlinc-embeddable:** An embeddable version of the Kotlin compiler is required to compile `.kt` source files into standard JVM bytecode (`.class` files).
* **d8:** This is the modern dexer for the Android platform. It takes `.class` files and converts them into the Dalvik Executable (`.dex`) format, which is the bytecode format executed by the Android Runtime (ART).
* **apksigner:** The final step. The `apksigner` tool signs the packaged APK with a bundled debug certificate, which is a prerequisite for the Android OS to allow its installation.

This direct orchestration enforces a simplified and conventional project structure. Projects must conform to a standard layout, such as `src/main/java`, `src/main/res`, and a single `src/main/AndroidManifest.xml`.

#### **Table 1: On-Device Build Toolchain Components**

| Tool | Function | Example On-Device Invocation | Origin / Notes |
| :---- | :---- | :---- | :---- |
| **aapt2** | Compiles Android resources into binary format and links them. | `aapt2 link -o out.apk -I android.jar --manifest M.xml -R res.zip` | Native ARM64/x86\_64 binary from Android SDK Build-Tools. |
| **kotlinc** | Compiles Kotlin/Java source code to JVM .class files. | `kotlinc -classpath android.jar -d classes_out src/` | Embeddable Kotlin compiler JAR, invoked via a shell script. |
| **d8** | Converts JVM .class files to Android's .dex format. | `d8 classes_out/\*\*/\*.class --lib android.jar --output dex_out/` | Part of the R8/D8 toolset, invoked as a JAR. Requires a mobile JVM. |
| **apksigner** | Signs the final APK with a debug certificate. | `apksigner sign --ks debug.keystore app-unsigned.apk` | Native binary or JAR from Android SDK Build-Tools. |
| **android.jar** | The Android platform API library stub. | N/A (Used as `-I` or `-classpath` argument) | A specific API level version (e.g., API 34\) bundled with the IDE. |

## **2. The Build Sequence**

The `On-Device Build Service` executes the following precise sequence of command-line invocations to transform a project's source code into a runnable APK.

1.  **Step 1: Resource Compilation (aapt2 compile):** The service first identifies all resource files within the project's `res/` directory. It then invokes `aapt2 compile` for each file, specifying the source directory and an output directory for the compiled, intermediate `.flat` files.
2.  **Step 2: Resource Linking (aapt2 link):** Once all resources are compiled, the service invokes `aapt2 link`. This command takes the compiled `.flat` files, the project's `AndroidManifest.xml`, and a reference to the platform's `android.jar`. This critical step links everything together to produce a preliminary `resources.apk` and, most importantly, generates the `R.java` file.
    * **Source Map Generation:** During this step, the service also runs a custom XML parser over the `res/layout/` directory. It extracts every `android:id="@+id/..."` attribute, its file path, and its line number, writing this data to the `source_map.json` file.
3.  **Step 3: Source Code Compilation (kotlinc):** With the `R.java` file now available, the service can compile the application's source code. It invokes the `kotlinc` compiler, providing a classpath that includes the platform `android.jar`, any library dependencies, and the source paths for both the user's code and the generated `R.java`.
4.  **Step 4: Dexing (d8):** The service then uses the `d8` tool to convert all the generated `.class` files into one or more `classes.dex` files.
5.  **Step 5: Final APK Packaging:** The final APK is a zip archive. The service creates this archive by combining the `resources.apk` from Step 2 with the `classes.dex` file(s) from Step 4.
6.  **Step 6: Signing (apksigner):** The unsigned APK must be signed to be installable. The service invokes `apksigner sign`, providing a bundled debug keystore and the path to the APK.
7.  **Step 7: Installation:** The final step is to trigger the installation of the signed APK. The service notifies the Host App of the final APK path, which then creates an `Intent` to present a system prompt to the user to install or update the application.

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