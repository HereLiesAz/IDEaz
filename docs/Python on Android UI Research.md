# **Architectural Blueprint for Embedded Python and Server-Driven UI in the IDEaz Environment**

## **1\. Introduction**

The landscape of mobile development involves a rigid compilation cycle—coding, compiling, packaging, and installing—that introduces significant friction into the creative process. The **IDEaz** project posits a radical shift: a "Post-Code" development environment where the integrated development environment (IDE) runs directly on the target device, racing local builds against remote continuous integration (CI) services to deliver the fastest possible iteration loop. While the current implementation leverages a custom, Gradle-free build pipeline for Kotlin and Android resources, a critical frontier remains: the integration of **Python** as a first-class citizen for dynamic logic and user interface (UI) orchestration.

This report serves as a definitive technical blueprint for implementing Python support within the IDEaz ecosystem. It specifically addresses the constraint of operating without a full Gradle build system on the device, a design choice necessitated by the resource limitations of mobile hardware and the project's architectural requirement for speed. The analysis focuses on two primary technical challenges: the manual embedding of the CPython runtime via the Java Native Interface (JNI) and the implementation of a Server-Driven UI (SDUI) architecture that allows Python code to drive native Android user interfaces, specifically **Jetpack Compose**, dynamically.

By synthesizing research on Android's native interface capabilities, the internal mechanics of existing Python-for-Android solutions, and emerging patterns in declarative UI rendering, this document outlines a robust, performant path toward a hybrid development model. This model effectively decouples application logic (Python) from the rendering engine (Kotlin/Compose), enabling "hot-reload" capabilities that bypass the traditional Android compilation lifecycle entirely.

## **2\. Architectural Context and Constraints**

To architect a viable solution, one must first deconstruct the host environment. IDEaz is not merely an editor; it is a build system, a version control client, and a runtime container. Its architectural decisions create specific constraints that disqualify standard off-the-shelf integration methods for Python.

### **2.1 The "Race to Build" Paradigm**

The central operational philosophy of IDEaz is the "Race to Build." Upon a user trigger, the system initiates two parallel processes:

1. **Remote Build:** A push to a GitHub repository triggers a GitHub Actions workflow to build the APK in the cloud.  
2. **Local Build:** An on-device service attempts to build the APK using bundled command-line tools.

The system installs whichever artifact completes first.1 This dual-pipeline approach is designed to mitigate the slowness of on-device compilation. However, introducing Python fundamentally alters this equation. As an interpreted language, Python does not require the heavy compilation steps (dexing, resource linking) that Java or Kotlin do. A Python-based logic layer can essentially "win" the race instantly, provided the native shell (the Android APK hosting the interpreter) is already installed. This capability transforms the development loop from **Edit-Compile-Install-Run** to **Edit-Sync-Reload**, aligning perfectly with the "Post-Code" vision of immediate feedback.

### **2.2 The "No-Gradle" Build Constraint**

Standard Android development relies heavily on Gradle to manage dependencies, merge resources, and configure native builds. Tools like **Chaquopy** 2 and **Kivy** 4 are designed as Gradle plugins. They hook into the Gradle task graph to:

* Download and extract the Python interpreter.  
* Compile Python code to bytecode (.pyc).  
* Package standard libraries into source sets.  
* Generate JNI initialization code.  
* Merge native libraries (.so files) for different ABIs (Application Binary Interfaces).

IDEaz, however, operates without Gradle to conserve device resources and maximize speed. It uses a manual orchestration of aapt2, kotlinc, d8, and apksigner.1 Consequently, the standard integration paths for Python are blocked. We cannot simply apply the com.chaquo.python plugin. Instead, we must reverse-engineer the tasks performed by these plugins and replicate them using the file manipulation and process execution capabilities available within the IDEaz BuildService.1 The solution must be a "manual injection" strategy that treats the Python runtime as just another set of assets and native libraries to be packaged into the final APK.

### **2.3 The Jetpack Compose Interoperability Challenge**

The second major constraint lies in the target UI framework: **Jetpack Compose**. Unlike the traditional Android View system, which relies on Java classes that can be instantiated via reflection (and thus accessed via bridges like **PyJnius** 5), Jetpack Compose is a compiler plugin.7

When a developer writes a @Composable function, the Kotlin compiler transforms it into a complex structure involving Composer parameters, restart groups, and synthetic integers for stability management.9 These structures do not exist as standard methods in the compiled bytecode and cannot be invoked via standard Java reflection.10 Therefore, a direct binding where Python calls compose.Text("Hello") is technically infeasible. This necessitates an indirect control mechanism—**Server-Driven UI (SDUI)**—where Python acts as a state engine emitting data (JSON) that a pre-compiled Kotlin rendering engine consumes to build the UI.11

| Feature | Standard Android/Gradle | IDEaz "No-Gradle" Environment | Implication for Python Support |
| :---- | :---- | :---- | :---- |
| **Build Tool** | Gradle (DSL, Task Graph) | Custom BuildOrchestrator (Kotlin Scripts) | Manual management of .so libs and assets required. |
| **Dependency Resolution** | Gradle/Maven Central | HttpDependencyResolver (Aether) | Must manually fetch/extract Python runtime AARs. |
| **Native Lib Packaging** | Auto-merge via AGP | Manual copy to jniLibs/ via ProcessAars | Need custom BuildStep to inject libpython.so. |
| **UI Framework** | Views / Compose | Jetpack Compose | Direct Python-to-UI bindings are impossible; requires SDUI. |

## **3\. Strategy 1: Manual Embedding of the Python Runtime**

The foundation of Python support in IDEaz depends on successfully embedding a CPython interpreter into the APK generated by the on-device build service. Since we cannot compile CPython from source on the device (lacking a C toolchain like GCC/Clang in the environment), we must leverage pre-built binaries.

### **3.1 Sourcing the Runtime Artifacts**

The most robust source for Android-compatible Python binaries is the **Chaquopy** project. Although designed as a Gradle plugin, Chaquopy publishes its runtime components as standard Maven artifacts (AARs).2 By analyzing the repository structure, we identify the core component: com.chaquo.python.runtime:chaquopy\_java.

This AAR contains:

* **JNI Libraries (jni/):** The cross-compiled libpython.so and the bridge library libchaquopy\_java.so for architectures like arm64-v8a and armeabi-v7a.14  
* **Assets (assets/):** The Python standard library (often zipped as stdlib.zip), bootstrap scripts (bootstrap.py), and support packages.15  
* **Java Classes (classes.jar):** The Java API for interacting with Python (e.g., Python.start(), PyObject).

### **3.2 The PythonInjector Build Step**

To integrate this into IDEaz, a new build step, PythonInjector, must be introduced into the BuildOrchestrator sequence.1 This step will execute prior to the ApkBuild step.

#### **3.2.1 Component Extraction**

The HttpDependencyResolver in IDEaz 1 is already capable of fetching artifacts. We configure it to download the Chaquopy runtime AAR. Once downloaded, the PythonInjector performs the following operations:

1. **Unzip the AAR:** Using java.util.zip.ZipInputStream, extract the contents of the AAR.  
2. **Native Library Placement:** Locate the .so files in the jni/ folder of the AAR. Copy these files to the project's build directory under app/build/intermediates/jniLibs/arm64-v8a/ (assuming the target device is ARM64). IDEaz’s app/build.gradle.kts configuration for jniLibs.useLegacyPackaging \= true 1 ensures that any libraries placed here are packaged into the lib/ directory of the final APK.  
3. **Asset Injection:** Extract the assets/ folder from the AAR and merge it with the project's src/main/assets/. This ensures the Python standard library is available at runtime.  
4. **Java Class Merging:** Extract classes.jar from the AAR. This JAR must be added to the classpath of the KotlincCompile step 1 so that user code can reference com.chaquo.python.Python. Additionally, it must be processed by D8Compile to be included in the final classes.dex.

#### **3.2.2 Manifest Configuration**

The Python runtime interacts with the Android system, requiring specific declarations in AndroidManifest.xml. The ProcessManifest step 1 must dynamically inject:

* **ABI Filters:** While Gradle usually handles this, in a manual build, we ensure only the supported architecture (e.g., arm64-v8a) libraries are copied to minimize APK size.  
* **Permissions:** If the Python script needs network access (for pip or SDUI), android.permission.INTERNET must be injected.17

### **3.3 Runtime Initialization Logic**

In a standard Chaquopy setup, a Gradle-generated class called BuildConfig or resource files populate the configuration for Python.start(). In IDEaz, we must construct this configuration manually at runtime.

#### **3.3.1 The Bootstrapping Problem**

Android applications cannot execute code or load arbitrary files directly from the APK's assets/ folder because these files are compressed. The Python interpreter requires a filesystem path to stdlib.zip and the user's source code to initialize sys.path.13

Therefore, the MainActivity of the user's project (or a base Application class) must include an initialization routine:

1. **Asset Extraction:** On the first run, copy the Python assets (stdlib.zip, main.py, etc.) from the APK's assets/ to the application's internal storage directory (context.getFilesDir()).19  
   * *Optimization:* Use checksums or version files to avoid re-extracting unchanged assets on subsequent launches.  
2. **Environment Setup:** Configure the AndroidPlatform object with the paths to the extracted files.  
   Java  
   // Conceptual initialization logic  
   if (\!Python.isStarted()) {  
       AndroidPlatform platform \= new AndroidPlatform(context);  
       // Manually point to extracted assets if necessary, though  
       // standard implementation often detects filesDir/python automatically.  
       Python.start(platform);  
   }

3. **Library Loading:** The system must manually call System.loadLibrary("chaquopy\_java") if the Java wrapper doesn't do it automatically. The libpythonX.Y.so is usually loaded as a dependency of the bridge library.21

### **3.4 Handling Python Packages (pip)**

A critical requirement is the ability to install third-party Python packages. Standard pip execution fails on Android because the platform lacks a C compiler (gcc/clang) for building extensions and standard filesystem layouts.23

#### **3.4.1 Pure Python Packages**

For packages that contain only Python code (no C extensions), IDEaz can implement a "download and unzip" strategy. The DependencyManager 1 can download the .whl (Wheel) file from PyPI, unzip it, and place it in the site-packages directory within the app's internal storage.25 The Python runtime will pick this up if sys.path is configured correctly.

#### **3.4.2 Binary Extensions (NumPy, Pandas)**

Packages with C extensions (e.g., NumPy) require compilation against the Android NDK. Since IDEaz does not include the NDK, users cannot compile these on-device. The solution is to leverage **pre-built wheels**.

* **Chaquopy Repository:** Chaquopy maintains a repository of Android-compatible wheels.13 IDEaz needs to be configured to search this repository.  
* **Installation Mechanism:** Instead of running pip install, IDEaz must download the specific Android-compatible wheel for the target ABI (e.g., numpy-1.21.0-cp38-cp38-android\_21\_arm64\_v8a.whl), extract it, and place it in the application's library path. This mimics the behavior of pip install \--no-index.28

## **4\. Strategy 2: Server-Driven UI (SDUI) Architecture**

With the Python runtime embedded, the challenge shifts to driving the UI. As established, direct interaction with Jetpack Compose via JNI/Reflection is blocked by the synthetic nature of compiled Composable functions.9 The most viable alternative is **Server-Driven UI (SDUI)**, adapted for a local context.

In this architecture, the Python "backend" runs locally on the device. It holds the application state and business logic. It emits a **JSON Schema** describing the UI. A pre-compiled Kotlin "Renderer" running in the Android UI thread consumes this JSON and maps it to native Jetpack Compose components.

### **4.1 The Localhost Loop Architecture**

This approach effectively treats the Android app as a browser, and the Python script as a local web server.

#### **4.1.1 The Local Python Server**

A lightweight web framework like **Flask** or **FastAPI** is ideal for this purpose.30

* **Execution:** The Python script starts an HTTP server binding to 127.0.0.1 (localhost) on a specific port (e.g., 5000).32  
* **Lifecycle:** To prevent the Android OS from killing the server process when the app is in the background, the server should be hosted within an Android **Foreground Service**.33 This requires the FOREGROUND\_SERVICE permission in the manifest.  
* **Endpoints:**  
  * GET /ui: Returns the current UI tree as JSON.  
  * POST /action: Receives user interactions (clicks, input) from the Android UI.

#### **4.1.2 The JSON Protocol (Schema)**

The schema acts as the contract between Python and Kotlin. It must be recursive and descriptive.

**Example Schema Structure:**

JSON

{  
  "type": "Scaffold",  
  "children":  
    }  
  \]  
}

This JSON structure is agnostic of the underlying implementation. The Python script generates this structure based on its internal state. For instance, when action\_increment is received, Python updates the counter variable and regenerates the JSON with "Counter Value: 6".

### **4.2 The Kotlin Renderer (Frontend)**

The Kotlin side acts as a "dumb" player.34 It is responsible for fetching the JSON, parsing it, and rendering the corresponding Composables.

#### **4.2.1 Data Fetching**

The app uses **Retrofit** or **OkHttp** to poll the Python server or establish a WebSocket connection for real-time updates.35

* **State Holder:** A ViewModel in Kotlin holds the current UiState (the JSON tree).  
* **Polling/Stream:** A coroutine continuously observes the server. When the Python backend pushes a new state (or responds to an action), the ViewModel updates the UiState, triggering a recomposition in Jetpack Compose.37

#### **4.2.2 The Recursive Renderer**

The core of the frontend is a recursive Composable function that acts as a factory.

Kotlin

@Composable  
fun DynamicUiRenderer(component: JsonComponent, onAction: (String) \-\> Unit) {  
    when (component.type) {  
        "Column" \-\> {  
            Column(modifier \= parseModifiers(component.properties)) {  
                component.children.forEach { child \-\>  
                    DynamicUiRenderer(child, onAction)  
                }  
            }  
        }  
        "Text" \-\> {  
            Text(text \= component.properties\["text"\]?: "")  
        }  
        "Button" \-\> {  
            Button(onClick \= { onAction(component.properties\["onClick"\]?: "") }) {  
                Text(text \= component.properties\["text"\]?: "")  
            }  
        }  
        //... mappings for Row, Image, TextField, etc.  
    }  
}

This pattern is well-documented in libraries such as json-to-compose and AndroidDynamicJetpackCompose.11 By pre-compiling this renderer into the IDEaz template, the user gains the ability to create complex UIs via Python without ever compiling Kotlin code.

### **4.3 Why SDUI is Superior to WebView**

While a WebView could also display a UI served by Python 40, SDUI offers distinct advantages aligned with IDEaz's goals:

1. **Native Performance:** The UI is rendered using native Jetpack Compose nodes, ensuring 60fps scrolling, native animations, and accessibility support.42  
2. **Look and Feel:** The application looks exactly like a standard Android app, adhering to Material Design 3 guidelines, rather than a web page.  
3. **State Management:** The "Post-Code" philosophy emphasizes interacting with the running app. SDUI keeps the "source of truth" in Python, allowing the AI agent to modify the Python state logic and see immediate results on the native UI without an APK rebuild.

## **5\. Alternative Architectures and Trade-offs**

### **5.1 Flet (Flutter-based Python)**

**Flet** is a framework that enables building Flutter apps with Python.43

* **Mechanism:** Flet acts as a generic Flutter engine that communicates with a local Python process.  
* **Pros:** It provides a polished, cross-platform UI out of the box.  
* **Cons for IDEaz:** It requires the Flutter engine, which is a massive dependency. Integrating Flutter *inside* an existing native Android view hierarchy (IDEaz uses Compose) is complex and heavy. It deviates from the goal of "driving native Android UIs."

### **5.2 Kivy (OpenGL)**

**Kivy** renders its own UI using OpenGL.4

* **Pros:** Mature, robust Python support.  
* **Cons for IDEaz:** Kivy apps do not look native. They use custom widgets that feel alien on Android. Furthermore, Kivy requires a specific activity bootstrap that conflicts with standard Android activities, making integration into a mixed environment difficult.45

### **5.3 PyJnius (Direct JNI)**

**PyJnius** allows Python to access Java classes via JNI reflection.5

* **Pros:** Direct access to Android system APIs (Sensors, Toast, Intent).  
* **Cons for IDEaz:** As noted, it cannot invoke Compose functions. It is, however, the perfect *complement* to SDUI.  
  * *Hybrid Model:* Use SDUI (JSON) for the visual layer and PyJnius for the logic layer (e.g., Python calls LocationManager via PyJnius, processes the coordinates, and updates the JSON state to show the location on a map).6

## **6\. Detailed Implementation Roadmap**

### **6.1 Phase 1: Toolchain Preparation (The PythonInjector)**

The immediate task is to enable the BuildService to package Python.

* **Step 1:** Define the com.chaquo.python:runtime dependency in libs.versions.toml to ensure version consistency.  
* **Step 2:** Modify the HttpDependencyResolver to support downloading AARs and extracting specific paths (jni/, assets/).  
* **Step 3:** Implement the PythonInjector build step. This step will copying the extracted .so files to app/src/main/jniLibs and the assets to app/src/main/assets/python.  
* **Step 4:** Update ProcessManifest to add android:extractNativeLibs="true" to the \<application\> tag, which is often required for loading native libraries from manually constructed APKs.46

### **6.2 Phase 2: The Runtime Bootstrap**

Develop a standardized Kotlin initialization class, PythonBootstrapper.kt, to be included in project templates.

* **Function:** fun initialize(context: Context)  
* **Logic:**  
  1. Check if filesDir/python exists. If not, unzip assets/python to filesDir.  
  2. Set PYTHONHOME environment variable to filesDir/python.  
  3. Call System.loadLibrary("pythonX.Y").  
  4. Initialize the Python thread.

### **6.3 Phase 3: The SDUI Template**

Create a "Python Native UI" project template in IDEaz.

* **main.py:** Contains a FastAPI app definition and the UI state logic.  
* **ui\_schema.py:** Helper classes in Python to generate the JSON schema (e.g., class Column, class Text), providing a developer-friendly API over raw JSON.  
* **MainActivity.kt:** The pre-compiled container. It starts the PythonService (foreground service hosting main.py) and sets the content to DynamicUiRenderer.

### **6.4 Phase 4: Hot Reload Implementation**

To achieve the "Post-Code" editing speed:

1. **File Watcher:** IDEaz monitors the project's src/python directory.  
2. **Sync:** When a file changes, IDEaz copies the modified file to the app's internal storage (filesDir/python).  
3. **Signal:** IDEaz sends a broadcast intent (e.g., com.ideaz.ACTION\_RELOAD\_PYTHON).  
4. **Reload:** The PythonService receives the intent, uses importlib.reload(main), and restarts the FastAPI server. The UI client (Kotlin) detects the server restart and re-fetches the state.

## **7\. Performance and Optimization**

### **7.1 Startup Time**

Python initialization on Android can take 1-3 seconds.

* **Mitigation:** Extract assets in a background thread during the first splash screen. Use a warm-up service to initialize the Python interpreter as soon as the app process starts, before the UI is rendered.

### **7.2 JSON Overhead**

Transferring large JSON trees for every frame is inefficient.

* **Mitigation:**  
  * **State Hoisting:** The JSON should define the *structure* once. Dynamic values (text, colors) should be bound to keys (e.g., {{counter}}). The server sends a small "Data" payload to update these keys, rather than resending the whole tree.47  
  * **Diffing:** Implementing DiffUtil-like logic in the Kotlin renderer to only recompose changed subtrees.

## **8\. Conclusion**

Implementing Python support in IDEaz without Gradle is a complex but feasible engineering challenge. It requires abandoning standard plugins in favor of low-level artifact manipulation and adopting a Server-Driven UI architecture to bridge the gap between Python logic and Jetpack Compose rendering.

By manually injecting the Python runtime and treating the UI as a remote projection of Python state, IDEaz can achieve a unique development velocity. This approach enables users to write Python code that drives native, high-performance Android UIs with hot-reload capabilities, fulfilling the "Post-Code" vision of fluid, immediate creation. The combination of **Chaquopy binaries**, **Manual JNI bootstrapping**, and **SDUI over Localhost** provides the optimal balance of performance, flexibility, and architectural decoupling required for this ambitious platform.

### **Summary of Key Recommendations**

1. **Extract, Don't Compile:** Use pre-built Chaquopy AARs for the runtime.  
2. **Inject Manually:** Use a custom BuildStep to place native libs and assets.  
3. **Use SDUI:** Drive Jetpack Compose via a JSON protocol emitted by a local Python server.  
4. **Hybrid Bridge:** Use SDUI for UI, PyJnius for system API access.  
5. **Hot Reload:** Implement file syncing and module reloading to bypass APK rebuilds for Python logic changes.

## ---

**9\. Feature Matrix: Architecture Comparison**

| Feature | Kivy / BeeWare | Chaquopy (Standard) | IDEaz (Proposed SDUI) |
| :---- | :---- | :---- | :---- |
| **Build System** | Gradle Required | Gradle Required | **No Gradle (Manual)** |
| **UI Rendering** | OpenGL / Canvas | Native Views (XML) | **Native Jetpack Compose** |
| **Logic Language** | Python | Python \+ Java | **Python** (Logic) \+ **Kotlin** (Renderer) |
| **Hot Reload** | Limited / Restart | No (Rebuild APK) | **Yes** (State/Logic reload) |
| **Native Look** | Poor (Custom widgets) | Good (Native Views) | **Excellent** (Material 3\) |
| **Complexity** | High (Custom stack) | Medium (Gradle plugin) | **High** (Custom architecture) |

## **10\. Data Tables**

### **10.1 Build Pipeline Injection Points**

| Build Step | Standard Action | Python Injection Action |
| :---- | :---- | :---- |
| ProcessAars | Extracts classes.jar, res/ | **Extract jni/ (libpython), assets/ (stdlib)** |
| ProcessManifest | Merges manifests | **Inject INTERNET permission, extractNativeLibs="true"** |
| KotlincCompile | Compiles user Kotlin | **Add chaquopy\_java.jar to classpath** |
| ApkBuild | Packages dex and res | **Include python/ folder in assets** |

### **10.2 JSON Protocol Mapping Example**

| Compose Component | JSON Type | Properties (Example) |
| :---- | :---- | :---- |
| Column | "Column" | {"verticalArrangement": "Center"} |
| Text | "Text" | {"text": "Hello", "fontSize": 24} |
| Button | "Button" | {"onClick": "submit\_form"} |
| Image | "Image" | {"url": "file:///storage/..."} |
| LazyColumn | "List" | {"items": \["item1", "item2"\]} |

#### **Works cited**

1. project\_backup\_full\_2025-12-19\_20-14-27.txt  
2. Chaquopy: the Python SDK for Android \- GitHub, accessed December 22, 2025, [https://github.com/chaquo/chaquopy](https://github.com/chaquo/chaquopy)  
3. Chaquopy – Python SDK for Android, accessed December 22, 2025, [https://chaquo.com/chaquopy/](https://chaquo.com/chaquopy/)  
4. Kivy on Android — Kivy 2.3.1 documentation, accessed December 22, 2025, [https://kivy.org/doc/stable/guide/android.html](https://kivy.org/doc/stable/guide/android.html)  
5. Implement Java interface from Python using PyJnius \- Stack Overflow, accessed December 22, 2025, [https://stackoverflow.com/questions/43016453/implement-java-interface-from-python-using-pyjnius](https://stackoverflow.com/questions/43016453/implement-java-interface-from-python-using-pyjnius)  
6. Android \- pyjnius 1.6.1 documentation, accessed December 22, 2025, [https://pyjnius.readthedocs.io/en/latest/android.html](https://pyjnius.readthedocs.io/en/latest/android.html)  
7. Compose Compiler | Jetpack \- Android Developers, accessed December 22, 2025, [https://developer.android.com/jetpack/androidx/releases/compose-compiler](https://developer.android.com/jetpack/androidx/releases/compose-compiler)  
8. Reverse-Engineering the Compose Compiler Plugin: Intercepting the Frontend, accessed December 22, 2025, [https://hinchman-amanda.medium.com/reverse-engineering-the-compose-compiler-plugin-intercepting-the-frontend-657162893b11](https://hinchman-amanda.medium.com/reverse-engineering-the-compose-compiler-plugin-intercepting-the-frontend-657162893b11)  
9. Why Calling a Composable from a Regular Function Isn't Possible \- ProAndroidDev, accessed December 22, 2025, [https://proandroiddev.com/why-calling-a-composable-from-a-regular-function-isnt-possible-b9d8f77b6658](https://proandroiddev.com/why-calling-a-composable-from-a-regular-function-isnt-possible-b9d8f77b6658)  
10. Hey everyone we ve internally been having problems with the kotlinlang \#compose, accessed December 22, 2025, [https://slack-chats.kotlinlang.org/t/29734094/hey-everyone-we-ve-internally-been-having-problems-with-the-](https://slack-chats.kotlinlang.org/t/29734094/hey-everyone-we-ve-internally-been-having-problems-with-the-)  
11. vvsdevs/AndroidDynamicJetpackCompose: Android Dynamic Jetpack Compose is a powerful library that enables dynamic layout rendering based on JSON configurations using Jetpack Compose. This library allows developers to design and update UI elements dynamically without needing to release a new app update, making it a flexible and efficient solution for Android applications. \- GitHub, accessed December 22, 2025, [https://github.com/vvsdevs/AndroidDynamicJetpackCompose](https://github.com/vvsdevs/AndroidDynamicJetpackCompose)  
12. Server Driven UI (SDUI): The Necessary Evil for Scalable Mobile Apps \- Medium, accessed December 22, 2025, [https://medium.com/digia-studio/server-driven-ui-sdui-the-necessary-evil-for-scalable-mobile-apps-80c650a2c8de](https://medium.com/digia-studio/server-driven-ui-sdui-the-necessary-evil-for-scalable-mobile-apps-80c650a2c8de)  
13. Gradle plugin \- Chaquopy 17.0, accessed December 22, 2025, [https://chaquo.com/chaquopy/doc/current/android.html](https://chaquo.com/chaquopy/doc/current/android.html)  
14. Extract code from .aar file Android \- Stack Overflow, accessed December 22, 2025, [https://stackoverflow.com/questions/34241411/extract-code-from-aar-file-android](https://stackoverflow.com/questions/34241411/extract-code-from-aar-file-android)  
15. Please add chaquopy support to be built into a library module (AAR) · Issue \#94 \- GitHub, accessed December 22, 2025, [https://github.com/chaquo/chaquopy/issues/94](https://github.com/chaquo/chaquopy/issues/94)  
16. Add C and C++ code to your project | Android Studio, accessed December 22, 2025, [https://developer.android.com/studio/projects/add-native-code](https://developer.android.com/studio/projects/add-native-code)  
17. How to access the local host server with web view in Android \- Stack Overflow, accessed December 22, 2025, [https://stackoverflow.com/questions/15103463/how-to-access-the-local-host-server-with-web-view-in-android](https://stackoverflow.com/questions/15103463/how-to-access-the-local-host-server-with-web-view-in-android)  
18. Question: Where does chaquo read py module? · Issue \#593 \- GitHub, accessed December 22, 2025, [https://github.com/chaquo/chaquopy/issues/593](https://github.com/chaquo/chaquopy/issues/593)  
19. Android \- Copy assets to internal storage \- Stack Overflow, accessed December 22, 2025, [https://stackoverflow.com/questions/19218775/android-copy-assets-to-internal-storage](https://stackoverflow.com/questions/19218775/android-copy-assets-to-internal-storage)  
20. Runtime Extraction of Android Assets \- j2i.net, accessed December 22, 2025, [https://blog.j2i.net/2021/07/06/runtime-extraction-of-android-assets/](https://blog.j2i.net/2021/07/06/runtime-extraction-of-android-assets/)  
21. java.lang.UnsatisfiedLinkError: dlopen failed: library "/Users/..." not found \- Stack Overflow, accessed December 22, 2025, [https://stackoverflow.com/questions/55083734/java-lang-unsatisfiedlinkerror-dlopen-failed-library-users-not-found](https://stackoverflow.com/questions/55083734/java-lang-unsatisfiedlinkerror-dlopen-failed-library-users-not-found)  
22. dlopen failed, tries to open PC file, not Android file · Issue \#669 · android/ndk-samples, accessed December 22, 2025, [https://github.com/android/ndk-samples/issues/669](https://github.com/android/ndk-samples/issues/669)  
23. 6\. Using Python on Android — Python 3.14.2 documentation, accessed December 22, 2025, [https://docs.python.org/3/using/android.html](https://docs.python.org/3/using/android.html)  
24. installing python packages on android \- Stack Overflow, accessed December 22, 2025, [https://stackoverflow.com/questions/2733971/installing-python-packages-on-android](https://stackoverflow.com/questions/2733971/installing-python-packages-on-android)  
25. What Are Python Wheels and Why Should You Care?, accessed December 22, 2025, [https://realpython.com/python-wheels/](https://realpython.com/python-wheels/)  
26. How do I install Python libraries in wheel format? \[duplicate\] \- Stack Overflow, accessed December 22, 2025, [https://stackoverflow.com/questions/21222114/how-do-i-install-python-libraries-in-wheel-format](https://stackoverflow.com/questions/21222114/how-do-i-install-python-libraries-in-wheel-format)  
27. FAQ \- Chaquopy 17.0, accessed December 22, 2025, [https://chaquo.com/chaquopy/doc/current/faq.html](https://chaquo.com/chaquopy/doc/current/faq.html)  
28. How to install, download and build Python wheels \- ActiveState, accessed December 22, 2025, [https://www.activestate.com/resources/quick-reads/python-install-wheel/](https://www.activestate.com/resources/quick-reads/python-install-wheel/)  
29. Install python wheel file without using pip \- Stack Overflow, accessed December 22, 2025, [https://stackoverflow.com/questions/36132350/install-python-wheel-file-without-using-pip](https://stackoverflow.com/questions/36132350/install-python-wheel-file-without-using-pip)  
30. How to Build a Simple Android App with Flask Backend? \- GeeksforGeeks, accessed December 22, 2025, [https://www.geeksforgeeks.org/android/how-to-build-a-simple-android-app-with-flask-backend/](https://www.geeksforgeeks.org/android/how-to-build-a-simple-android-app-with-flask-backend/)  
31. Setting up FastAPI development on Mobile \- DEV Community, accessed December 22, 2025, [https://dev.to/lexzee/setting-up-fastapi-development-on-mobile-5ei0](https://dev.to/lexzee/setting-up-fastapi-development-on-mobile-5ei0)  
32. Run a Server Manually \- FastAPI, accessed December 22, 2025, [https://fastapi.tiangolo.com/deployment/manually/](https://fastapi.tiangolo.com/deployment/manually/)  
33. Services \- python-for-android 2024.01.21 documentation, accessed December 22, 2025, [https://python-for-android.readthedocs.io/en/latest/services.html](https://python-for-android.readthedocs.io/en/latest/services.html)  
34. RemoteCompose: Another Paradigm for Server-Driven UI in Jetpack Compose | by Jaewoong Eum | Nov, 2025 | ProAndroidDev, accessed December 22, 2025, [https://proandroiddev.com/remotecompose-another-paradigm-for-server-driven-ui-in-jetpack-compose-92186619ba8f](https://proandroiddev.com/remotecompose-another-paradigm-for-server-driven-ui-in-jetpack-compose-92186619ba8f)  
35. json — JSON encoder and decoder — Python 3.14.2 documentation, accessed December 22, 2025, [https://docs.python.org/3/library/json.html](https://docs.python.org/3/library/json.html)  
36. Android \- JSON Parsing Using Retrofit Library with Jetpack Compose \- GeeksforGeeks, accessed December 22, 2025, [https://www.geeksforgeeks.org/kotlin/android-json-parsing-using-retrofit-library-with-jetpack-compose/](https://www.geeksforgeeks.org/kotlin/android-json-parsing-using-retrofit-library-with-jetpack-compose/)  
37. State and Jetpack Compose | Android Developers, accessed December 22, 2025, [https://developer.android.com/develop/ui/compose/state](https://developer.android.com/develop/ui/compose/state)  
38. State in Jetpack Compose \- Android Developers, accessed December 22, 2025, [https://developer.android.com/codelabs/jetpack-compose-state](https://developer.android.com/codelabs/jetpack-compose-state)  
39. jesusdmedinac/json-to-compose \- GitHub, accessed December 22, 2025, [https://github.com/jesusdmedinac/json-to-compose](https://github.com/jesusdmedinac/json-to-compose)  
40. Android WebView Example Tutorial \- DigitalOcean, accessed December 22, 2025, [https://www.digitalocean.com/community/tutorials/android-webview-example-tutorial](https://www.digitalocean.com/community/tutorials/android-webview-example-tutorial)  
41. Access a local development server from WebView | Views \- Android Developers, accessed December 22, 2025, [https://developer.android.com/develop/ui/views/layout/webapps/access-local-server](https://developer.android.com/develop/ui/views/layout/webapps/access-local-server)  
42. Compose Multiplatform – Beautiful UIs Everywhere \- JetBrains, accessed December 22, 2025, [https://www.jetbrains.com/compose-multiplatform/](https://www.jetbrains.com/compose-multiplatform/)  
43. Host app as a dynamic website \- Flet, accessed December 22, 2025, [https://flet.dev/docs/publish/web/dynamic-website/](https://flet.dev/docs/publish/web/dynamic-website/)  
44. Build a complete mobile | Desktop | Web app with Python \- Python Flet(Flutter) tutorial, accessed December 22, 2025, [https://www.youtube.com/watch?v=6Tj8\_iKqh\_k](https://www.youtube.com/watch?v=6Tj8_iKqh_k)  
45. Android apps with Python, Flask and a WebView \- Kivy Blog, accessed December 22, 2025, [https://blog.kivy.org/2016/05/android-apps-with-python-flask-and-a-webview/](https://blog.kivy.org/2016/05/android-apps-with-python-flask-and-a-webview/)  
46. Executing Compiled C Binary in Mobile App Without Root Access : r/androiddev \- Reddit, accessed December 22, 2025, [https://www.reddit.com/r/androiddev/comments/193imrb/executing\_compiled\_c\_binary\_in\_mobile\_app\_without/](https://www.reddit.com/r/androiddev/comments/193imrb/executing_compiled_c_binary_in_mobile_app_without/)  
47. Leveraging BFF and Dynamic UI in Android Applications with Jetpack Compose, accessed December 22, 2025, [https://basaransuleyman.medium.com/leveraging-bff-and-dynamic-ui-in-android-applications-with-jetpack-compose-27d81edb7c7c](https://basaransuleyman.medium.com/leveraging-bff-and-dynamic-ui-in-android-applications-with-jetpack-compose-27d81edb7c7c)