# **Dynamic Runtime Architectures for Mobile IDEs: The "No-Build" Paradigm**

## **Executive Summary**

The prevailing methodology for Android application development is predicated on a compilation-heavy lifecycle: code is written, compiled into bytecode, packaged into an APK (Android Package Kit), signed, and installed via the operating system's package manager. This cycle, governed largely by the Gradle build system and the Android Runtime (ART), introduces significant latency—often measuring in minutes—between code modification and execution. For the development of a mobile Integrated Development Environment (IDE) that runs directly on an Android device, this traditional loop is catastrophic to the user experience. It imposes heavy I/O operations, high CPU usage, and battery drain, while severing the immediate feedback loop required for modern exploratory programming.

This report investigates a radical architectural alternative proposed by the user: a "No-Build" mobile IDE. In this paradigm, the user's project is not an independent artifact but a dynamic extension of the IDE itself. The IDE functions as a generic "Host," and the user's application acts as a "Guest" or plugin, defined by a schema of UI components and a scriptable logic layer. By decoupling the application's definition from its binary execution, the IDE can instantly "inflate" the user's project without engaging the native Android build toolchain.

The analysis synthesizes data from server-driven UI (SDUI) frameworks, embedded JavaScript runtimes, and Android security protocols. We identify that while direct native code generation (Dex loading) is increasingly inviable due to Android 14's W^X (Write XOR Execute) security restrictions 1, a hybrid architecture combining declarative UI schemas (specifically Cash App's Redwood) and embedded JavaScript engines (Zipline/QuickJS) offers a robust solution.2 This report details the technical implementation of such a system, exploring the necessary mechanisms for dynamic screen generation, logic bridging, state preservation during hot reloads, and the integration of a client-side Local Language Server Protocol (LSP) to ensure a professional-grade development environment.

## ---

**1\. The Compilation Bottleneck and the "Extension" Paradigm**

To address the requirement of "skipping the build," we must first dissect the anatomy of the standard Android build process to understand what is being skipped and the implications of doing so.

### **1.1 The Anatomy of the Android Build Cycle**

In a standard development environment, the transformation from source code to running application involves a complex pipeline:

1. **Resource Merging:** XML layouts and assets are compiled into a binary format (resources.arsc) by the Android Asset Packaging Tool (AAPT2).  
2. **Compilation:** Kotlin/Java source code is compiled into JVM bytecode (.class files).  
3. **Dexing:** JVM bytecode is translated into Dalvik bytecode (.dex files) by the D8 compiler. This step includes desugaring (backporting Java 8+ features) and is computationally expensive.  
4. **Packaging & Signing:** The .dex files and resources are zipped into an APK and signed with a cryptographic key.  
5. **Installation:** The OS verifies the signature, extracts native libraries, and registers the app as a new user (UID) in the Linux kernel.4

For a mobile IDE attempting to run on the device it develops for, steps 3, 4, and 5 are the primary bottlenecks. Running the D8 compiler on a mobile processor is significantly slower than on a desktop workstation.5 Furthermore, step 5 (Installation) typically interrupts the IDE's process or requires user confirmation, breaking the "flow" state of development.

### **1.2 The "IDE as Host" Conceptual Model**

The user's query suggests creating the project as an "extension of itself." In software architecture, this maps to a **Plugin Architecture** or a **Host-Guest Model**.

In this model, the IDE is not a "builder" of apps, but a "player" of apps. The IDE is a pre-compiled, signed, and installed APK that contains a superset of all capabilities the user might need (a "Meta-Application"). The user's project becomes a configuration file—or a set of instructions—that tells the Meta-Application how to behave.

| Feature | Standard "Build" Model | Proposed "Extension" Model |
| :---- | :---- | :---- |
| **Execution Context** | User App runs in its own process/Sandbox. | User App runs *inside* the IDE's process. |
| **Permissions** | User App declares its own permissions. | User App inherits the IDE's permissions. |
| **UI Definition** | XML/Compose compiled to binary. | JSON/Script interpreted at runtime. |
| **Logic Definition** | Compiled to DEX bytecode. | Transpiled to Script/Bytecode (JS/Wasm). |
| **Update Mechanism** | Reinstall APK. | Hot-swap Script/Data in memory. |

This architectural shift moves the engineering challenge from *compilation* (generating native binaries) to *runtime composition* (interpreting instructions). The feasibility of this relies entirely on the capability of the runtime to simulate native behavior with sufficient performance and fidelity.

## ---

**2\. Runtime Architecture and Security Constraints**

Implementing the "Extension" model requires a mechanism to load and execute code dynamically. Historically, Android provided robust tools for this, but the security landscape has shifted dramatically.

### **2.1 The Decline of Native Dynamic Loading**

In early Android versions, an IDE could compile code to a .dex file, save it to the app's private storage, and load it using DexClassLoader. This would allow the user's code to run with near-native performance.6

#### **2.1.1 The W^X Violation (Android 14\)**

The viability of DexClassLoader for on-device development has been severely curtailed by the introduction of W^X (Write XOR Execute) memory protection in Android 14 (API Level 34).1

* **The Restriction:** The operating system dictates that memory pages can be either writable or executable, but never both simultaneously. This prevents an application from downloading (writing) code to a file and then mapping it into memory for execution.8  
* **Implication for IDEs:** An IDE cannot simply "compile" a user's Kotlin file to a .dex file in the /data/data/... directory and load it. Attempting to do so triggers a SecurityException.8

#### **2.1.2 InMemoryDexClassLoader: A Partial Solution?**

Android provides InMemoryDexClassLoader, which allows loading DEX data directly from a ByteBuffer in memory, bypassing the filesystem and theoretically adhering to W^X restrictions (since the memory is never "written" to disk as an executable).6  
However, utilizing this requires the IDE to generate valid DEX bytecode in memory. This reintroduces the D8 Compiler Bottleneck. To use InMemoryDexClassLoader, the IDE must include the D8 libraries, creating a bloated application size and subjecting the user to the slow compilation speeds of the native toolchain.5  
Consequently, for a "No-Build" experience, native DEX generation is a dead end. We must look to **virtualized runtimes** that execute code without generating Android-specific bytecode.

### **2.2 The Plugin Framework Legacy**

The concept of running an APK without installation was explored by frameworks like **DroidPlugin** and **Shadow** (referenced as "PluginManager" techniques).10 These frameworks hooked into the Android system services (ActivityManager, PackageManager) to intercept calls and proxy them to a guest APK.

* **Mechanism:** They use extensive reflection to trick the system into thinking the Host app is actually the Guest app.  
* **Relevance:** While they technically allow "running without installation," they are notoriously unstable and often break with each new Android version due to their reliance on private system APIs (Greylist/Blacklist restrictions).12 They are unsuitable for a modern, stable IDE architecture.

### **2.3 The Hybrid Host-Guest Architecture**

The optimal path forward, supported by the research, is a **Hybrid Host-Guest Architecture**. This involves:

1. **UI Layer:** A **Server-Driven UI (SDUI)** engine that inflates native widgets based on a schema.  
2. **Logic Layer:** An embedded **JavaScript or WebAssembly (Wasm)** engine to execute business logic.

This architecture is exemplified by **Cash App's Treehouse** (using Redwood and Zipline), which was designed specifically to update app functionality without re-releasing the APK.2 This is effectively the same problem set as "running a user project without rebuilding the IDE."

## ---

**3\. The UI Layer: Server-Driven UI (SDUI) on Localhost**

The user asks if it is possible to "dynamically generate a screen... with full functionality." In the native Android model, screens (Activity or Fragment classes) are static. To make them dynamic, we treat the screen definition as data.

### **3.1 Taxonomy of Dynamic UI Engines**

There are two primary approaches to defining UI as data:

| Approach | Description | Examples | Applicability to IDE |
| :---- | :---- | :---- | :---- |
| **HTML-Like / XML** | Using a markup language to describe views. The client parses the XML and instantiates Views. | **Hyperview** 13, **Protobuf** | Good for static content, but interactions are often limited to simple navigation or require server round-trips.14 |
| **JSON / Compose Tree** | Using a JSON structure that maps directly to the component tree of a declarative framework (Jetpack Compose). | **AndroidDynamicJetpackCompose** 15, **Redwood** 2 | Excellent. Maps cleanly to modern UI toolkits. Supports complex, nested hierarchies. |

### **3.2 The Redwood Schema Model**

**Redwood** stands out as the premier solution for this use case because it allows the user to write **Kotlin**, not JSON, yet executes dynamically.16

#### **3.2.1 The Schema Definition**

The IDE (Host) defines a **Schema**—a contract of available UI components. This acts as the "Standard Library" for the user's project.17

Kotlin

// Example Schema in Host  
@Widget(1)  
data class Button(  
    @Property(1) val text: String,  
    @Property(2) val onClick: () \-\> Unit  
)

The IDE compiles this schema into its native binary. It includes a "Widget Factory" that knows how to turn a Button request into a android.widget.Button or a Compose Button.16

#### **3.2.2 The User's Perspective**

In the IDE, the user writes standard-looking Kotlin code using these widgets.

Kotlin

// User Code (Guest)  
Column {  
    Text("Hello World")  
    Button("Click Me", onClick \= {... })  
}

Crucially, this code is not compiled to DEX. It is compiled (transpiled) to JavaScript (via Kotlin/JS).18  
The Redwood runtime inside the IDE executes this JS. The JS emits a stream of "DOM-like" operations (Create Node, Set Property, Append Child) which bridge over to the Android Host, creating the actual native UI.19  
**Insight:** This fulfills the user's requirement of "dynamic generation" because the UI tree is constructed at runtime by the JS engine. Changing the UI is as simple as changing the JS file and reloading the engine—a sub-second operation.

### **3.3 Handling Assets Dynamically**

A native app uses R.drawable.my\_image to reference assets. This relies on the AAPT2 build step generating unique integer IDs. A "No-Build" IDE cannot use AAPT2.

* **Solution:** The IDE must implement a dynamic asset loader.20  
* **Mechanism:** The IDE embeds a local HTTP server (using **Ktor** or **NanoHTTPD**) that serves the user's project directory.21  
* **Usage:** The user's code references images by path (e.g., "assets/logo.png"). The generic Image widget in the Redwood schema takes a URL string, not a resource ID. The IDE's widget implementation uses an image loading library (like Coil or Glide) to fetch the image from the local Ktor server.

## ---

**4\. The Logic Layer: Embedded Scripting Engines**

While SDUI handles the *look*, the user specifies "full functionality." This implies state management, network calls, database access, and conditional logic. SDUI frameworks often delegate logic to the server, but an IDE's apps must run logic on the device.

### **4.1 Zipline: The QuickJS Bridge**

**Zipline** (formerly part of Treehouse) is the logic engine that powers Redwood.3 It addresses the Android W^X limitation by using an embedded JavaScript engine, **QuickJS**.

#### **4.1.1 Why QuickJS?**

* **Size:** QuickJS is extremely small (embeddable in KB), minimizing the IDE's APK size.3  
* **Performance:** It supports pre-compiled bytecode (QuickJS bytecode), which is faster to load than parsing raw JS source.23  
* **Isolation:** Each run of the user's app can occur in a fresh QuickJS context, ensuring that a crash in the user's code doesn't crash the IDE (if handled correctly via try-catch blocks at the bridge level).

#### **4.1.2 The JNI Bridge**

The critical component is the bridge between the JS runtime and the Android Host. Zipline allows defining interfaces that extend ZiplineService.3

* **Host Implementation:** The IDE implements services like NetworkService, DatabaseService, or ToastService.  
* **Guest Consumption:** The user's code "requests" these services.  
  Kotlin  
  // User code  
  val toaster \= zipline.take\<ToastService\>("toast")  
  toaster.show("Hello from dynamic code\!")

* **Mechanism:** When toaster.show is called, Zipline serializes the arguments (using kotlinx.serialization) into JSON/internal format, passes them across the Java Native Interface (JNI) to the QuickJS engine, which invokes the corresponding native method in the IDE.3

### **4.2 Kotlin-to-JS Transpilation on Device**

To maintain the illusion that the user is writing Kotlin apps, the IDE must perform Kotlin-to-JS transpilation.

* **Challenge:** The standard kotlinc compiler is heavy.  
* **Optimization:** The IDE can utilize the **Kotlin JS IR** backend. While running the full compiler on Android is resource-heavy, it is less intensive than the full Android D8 pipeline.  
* **Incremental Compilation:** To ensure the "No-Build" feel, the IDE should only re-transpile changed files. Zipline supports hot-reloading code modules.23

### **4.3 Alternative: Kotlin/Wasm (The Future)**

As **Kotlin/Wasm** moves from Alpha to Beta, it presents a potentially superior target than JS.24

* **Performance:** Wasm executes at near-native speed, significantly faster than interpreted JS.24  
* **Interoperability:** Wasm is designed for secure sandboxing.  
* **Status:** Currently, Kotlin/Wasm support in libraries (like Zipline) is experimental (dubbed "Zipline 2.0").18 For a robust report, we acknowledge this as the immediate future roadmap for high-performance dynamic IDEs.

## ---

**5\. State Management and Hot Reload**

A critical "unsatisfied requirement" from the initial analysis is *how* the app maintains state when the IDE reloads the code. If every code change resets the app to the login screen, the "extension" model loses its utility.

### **5.1 The "Molecule" Pattern**

Cash App utilizes a library called **Molecule** to manage state in this architecture.25

* **Concept:** Molecule runs a Composable function solely to generate a StateFlow of data. It separates the *generation* of state from the *rendering* of UI.  
  Kotlin  
  // User Logic  
  @Composable  
  fun CounterPresenter(): StateFlow\<Int\> {  
      var count by remember { mutableStateOf(0) }  
      // logic to increment count  
      return count  
  }

* **Application:** In the Nexus IDE, the logic (Presenter) runs in Zipline. When the user changes a UI label, only the *UI* code reloads. The Zipline logic (if unchanged) can persist.  
* **State Serialization:** If the Logic itself changes, the IDE can capture the current StateFlow value, restart the Zipline engine with the new code, and re-inject the previous state value. This effectively enables "Hot Swap" functionality similar to Flutter's Hot Reload.23

## ---

**6\. The Editor Experience: The IDE Frontend**

We have established how the *User App* runs. But what about the IDE itself? It needs a code editor.

### **6.1 The WebView Editor Gap**

Native Android EditText components are ill-suited for code editing (poor performance with large text, no syntax highlighting API).

* **Solution:** Use a **WebView** hosting **Monaco Editor** (VS Code's core) or **CodeMirror**.27  
* **Integration:** The IDE loads a local HTML file.  
* **Bridge:** The IDE communicates with the editor via WebView.addJavascriptInterface.

### **6.2 Client-Side Language Server Protocol (LSP)**

To provide "full functionality," the IDE needs autocomplete and error checking.

* **Challenge:** Standard LSPs are heavy processes.  
* **Implementation:** The IDE can implement a lightweight LSP client in Kotlin. It parses the user's code (using a parser generator like ANTLR or a lightweight Kotlin PSI parser) and sends standard LSP JSON-RPC messages to the Monaco editor in the WebView.29  
  * *Example:* When the user types ., the IDE parses the variable type, finds available methods in the **Schema**, and sends a textDocument/completion response to Monaco.

## ---

**7\. Proposed Reference Architecture: "Nexus IDE"**

Based on the synthesis of the above technologies, we define the concrete architecture for the "Nexus IDE."

### **7.1 Component Diagram**

| Layer | Component | Implementation | Function |
| :---- | :---- | :---- | :---- |
| **IDE Frontend** | Editor View | Monaco (WebView) | Text editing, Syntax highlighting. |
| **IDE Backend** | Compiler Service | Embedded Kotlin/JS Compiler | Transpiles User Kotlin \-\> JS. |
| **IDE Backend** | Asset Server | Ktor Embedded | Serves images/JS to Runtime. |
| **Runtime** | Logic Engine | Zipline (QuickJS) | Executes user logic. |
| **Runtime** | UI Engine | Redwood Host | Renders native Android Views. |

### **7.2 The "Run" Lifecycle**

1. **User Action:** User taps "Run" (or "Hot Reload").  
2. **Compilation:** The Compiler Service identifies changed .kt files and transpiles them to a project.js bundle.31  
3. **Deployment:** The project.js is placed in the Ktor server's hosted directory.  
4. **Notification:** The Runtime is notified of the new bundle version via a WebSocket or callback.  
5. **Hot Load:**  
   * The Zipline engine pauses.  
   * State is serialized (if possible).  
   * The new project.js is loaded into QuickJS.23  
   * Zipline reconnects the Redwood bridge.  
6. **Render:** The new logic executes main(), producing a new widget tree. Redwood applies the diff to the screen.

### **7.3 Data Flow: User Interaction**

1. User taps a Button in the preview.  
2. **Android View Layer:** The native android.widget.Button receives onClick.  
3. **Redwood Bridge:** The event is serialized (e.g., { "id": 42, "event": "click" }).  
4. **Zipline:** The event crosses into QuickJS.  
5. **User Logic:** The JS callback onClick() defined by the user is executed.  
6. **State Update:** The user logic updates a reactive variable (count++).  
7. **Recomposition:** The UI tree regenerates in JS.  
8. **Redwood Bridge:** A layout diff is sent back to Android ({ "id": 43, "type": "Text", "value": "Count: 1" }).  
9. **Android View Layer:** The native TextView updates its text.

## ---

**8\. Feasibility and Future Roadmap**

### **8.1 Google Play Compliance**

A significant concern for such an IDE is Google Play Policy regarding "downloading executable code."

* **Policy:** Google allows code push (updating JS bundles) as long as the *primary purpose* of the app doesn't change.32  
* **IDE Context:** For an IDE, executing code *is* the primary purpose. As long as the IDE doesn't use this mechanism to covertly install malware or change its own nature (e.g., becoming a banking app), this architecture is compliant. However, distributing the *created* apps to other users would likely require the standard APK build process or a proprietary "Player" app installation.

### **8.2 Performance vs. Native**

* **UI:** Identical to native (100% fidelity).  
* **Logic:** 2x-5x slower than native for compute-heavy tasks (due to JS interpretation).33 For standard UI logic (form validation, network requests), this is imperceptible.  
* **Startup:** Instant (no process fork required).

### **8.3 Conclusion**

The "No-Build" mobile IDE is not only possible but represents the cutting edge of mobile architecture. By adopting a **Host-Guest** model powered by **Redwood** and **Zipline**, an IDE can treat user projects as dynamic extensions, bypassing the cumbersome Android build chain entirely. This enables a "write-and-see" feedback loop previously available only to web developers, effectively bringing the "Localhost" experience to native Android development. The limitations of W^X and compilation times are circumvented by shifting the paradigm from *binary generation* to *runtime interpretation*.

As **Kotlin/Wasm** matures, this architecture will likely become the standard for mobile development tooling, blurring the line between the IDE and the application it creates.

#### **Works cited**

1. Behavior changes: Apps targeting Android 14 or higher, accessed December 19, 2025, [https://developer.android.com/about/versions/14/behavior-changes-14](https://developer.android.com/about/versions/14/behavior-changes-14)  
2. Presentation: Playing in the Treehouse with Redwood and Zipline \- Jake Wharton, accessed December 19, 2025, [https://jakewharton.com/playing-in-the-treehouse-with-redwood-and-zipline/](https://jakewharton.com/playing-in-the-treehouse-with-redwood-and-zipline/)  
3. cashapp/zipline: Run Kotlin/JS libraries in Kotlin/JVM and Kotlin/Native programs \- GitHub, accessed December 19, 2025, [https://github.com/cashapp/zipline](https://github.com/cashapp/zipline)  
4. Application fundamentals | App architecture \- Android Developers, accessed December 19, 2025, [https://developer.android.com/guide/components/fundamentals](https://developer.android.com/guide/components/fundamentals)  
5. How to use Kotlin compiler on Android, accessed December 19, 2025, [https://discuss.kotlinlang.org/t/how-to-use-kotlin-compiler-on-android/6513](https://discuss.kotlinlang.org/t/how-to-use-kotlin-compiler-on-android/6513)  
6. Gyoonus/android\_dynamic\_loader: Android Application Dynamic Loader Using InmemoryDexClassLoader \- GitHub, accessed December 19, 2025, [https://github.com/Gyoonus/android\_dynamic\_loader](https://github.com/Gyoonus/android_dynamic_loader)  
7. Custom Class Loading in Dalvik \- Android Developers Blog, accessed December 19, 2025, [https://android-developers.googleblog.com/2011/07/custom-class-loading-in-dalvik.html](https://android-developers.googleblog.com/2011/07/custom-class-loading-in-dalvik.html)  
8. Support incremental installation on Android 14 · Issue \#20456 · bazelbuild/bazel \- GitHub, accessed December 19, 2025, [https://github.com/bazelbuild/bazel/issues/20456](https://github.com/bazelbuild/bazel/issues/20456)  
9. 3 ways for Dynamic Code Loading in Android | \- erev0s.com, accessed December 19, 2025, [https://erev0s.com/blog/3-ways-for-dynamic-code-loading-in-android/](https://erev0s.com/blog/3-ways-for-dynamic-code-loading-in-android/)  
10. DroidPluginTeam/DroidPlugin: A plugin framework on android,Run any third-party apk without installation, modification or repackage \- GitHub, accessed December 19, 2025, [https://github.com/DroidPluginTeam/DroidPlugin](https://github.com/DroidPluginTeam/DroidPlugin)  
11. Plugin modules in Android apps \- by Marcin Korniluk \- Medium, accessed December 19, 2025, [https://medium.com/@Zielony/plugin-modules-in-android-apps-3b3629d5a7d9](https://medium.com/@Zielony/plugin-modules-in-android-apps-3b3629d5a7d9)  
12. DON'T LET YOUR APP PLAY AS AN ANDROID PLUGIN \- Black Hat, accessed December 19, 2025, [https://blackhat.com/docs/asia-17/materials/asia-17-Luo-Anti-Plugin-Don't-Let-Your-App-Play-As-An-Android-Plugin-wp.pdf](https://blackhat.com/docs/asia-17/materials/asia-17-Luo-Anti-Plugin-Don't-Let-Your-App-Play-As-An-Android-Plugin-wp.pdf)  
13. Hyperview · Native mobile apps, as easy as creating a web site, accessed December 19, 2025, [https://hyperview.org/](https://hyperview.org/)  
14. Client-side form validation · Instawork hyperview · Discussion \#332 \- GitHub, accessed December 19, 2025, [https://github.com/Instawork/hyperview/discussions/332](https://github.com/Instawork/hyperview/discussions/332)  
15. vvsdevs/AndroidDynamicJetpackCompose: Android Dynamic Jetpack Compose is a powerful library that enables dynamic layout rendering based on JSON configurations using Jetpack Compose. This library allows developers to design and update UI elements dynamically without needing to release a new app update, making it a flexible and efficient solution for Android applications. \- GitHub, accessed December 19, 2025, [https://github.com/vvsdevs/AndroidDynamicJetpackCompose](https://github.com/vvsdevs/AndroidDynamicJetpackCompose)  
16. Native UI and multiplatform Compose with Redwood | Cash App Code Blog, accessed December 19, 2025, [https://code.cash.app/native-ui-and-multiplatform-compose-with-redwood](https://code.cash.app/native-ui-and-multiplatform-compose-with-redwood)  
17. Getting Dynamic | RedwoodJS Docs \- RedwoodSDK, accessed December 19, 2025, [https://docs.redwoodjs.com/docs/1.x/tutorial/chapter2/getting-dynamic](https://docs.redwoodjs.com/docs/1.x/tutorial/chapter2/getting-dynamic)  
18. Dynamic Kotlin with Zipline | Cash App Code Blog, accessed December 19, 2025, [https://code.cash.app/zipline](https://code.cash.app/zipline)  
19. 0.18.0 · cashapp redwood · Discussion \#2723 \- GitHub, accessed December 19, 2025, [https://github.com/cashapp/redwood/discussions/2723](https://github.com/cashapp/redwood/discussions/2723)  
20. Serving static content | Ktor Documentation, accessed December 19, 2025, [https://ktor.io/docs/server-static-content.html](https://ktor.io/docs/server-static-content.html)  
21. HTTP Server Inside Android App?. Turn your Android device into a… | by Zahid | Medium, accessed December 19, 2025, [https://medium.com/@zahidaz/building-a-pocket-sized-web-server-how-to-run-an-http-server-inside-your-android-app-4a1e735eb7e1](https://medium.com/@zahidaz/building-a-pocket-sized-web-server-how-to-run-an-http-server-inside-your-android-app-4a1e735eb7e1)  
22. NanoHttpd/nanohttpd: Tiny, easily embeddable HTTP server in Java. \- GitHub, accessed December 19, 2025, [https://github.com/NanoHttpd/nanohttpd](https://github.com/NanoHttpd/nanohttpd)  
23. How We Sped Up Zipline Hot Reload | Cash App Code Blog, accessed December 19, 2025, [https://code.cash.app/how-we-sped-up-zipline-hot-reload](https://code.cash.app/how-we-sped-up-zipline-hot-reload)  
24. Kotlin/Wasm | Kotlin Documentation, accessed December 19, 2025, [https://kotlinlang.org/docs/wasm-overview.html](https://kotlinlang.org/docs/wasm-overview.html)  
25. Molecule: Build a StateFlow stream using Jetpack Compose | Cash App Code Blog, accessed December 19, 2025, [https://code.cash.app/bridge-between-your-code-and-compose](https://code.cash.app/bridge-between-your-code-and-compose)  
26. cashapp/molecule: Build a StateFlow stream using Jetpack Compose \- GitHub, accessed December 19, 2025, [https://github.com/cashapp/molecule](https://github.com/cashapp/molecule)  
27. flutter\_monaco — Monaco (VS Code's editor) inside Flutter apps (Android/iOS/macOS/Windows) : r/FlutterDev \- Reddit, accessed December 19, 2025, [https://www.reddit.com/r/FlutterDev/comments/1munsu1/flutter\_monaco\_monaco\_vs\_codes\_editor\_inside/](https://www.reddit.com/r/FlutterDev/comments/1munsu1/flutter_monaco_monaco_vs_codes_editor_inside/)  
28. Replit — Comparing Code Editors: Ace, CodeMirror and Monaco, accessed December 19, 2025, [https://blog.replit.com/code-editors](https://blog.replit.com/code-editors)  
29. How to embed a Monaco Editor in a browser as a part of my first task at TypeFox, accessed December 19, 2025, [https://www.typefox.io/blog/how-to-embed-a-monaco-editor-in-a-browser-as-a-part-of-my-first-task-at-typefox/](https://www.typefox.io/blog/how-to-embed-a-monaco-editor-in-a-browser-as-a-part-of-my-first-task-at-typefox/)  
30. Integrating LSP with the Monaco Code Editor | by zsheng \- Medium, accessed December 19, 2025, [https://medium.com/@zsh-eng/integrating-lsp-with-the-monaco-code-editor-b054e9b5421f](https://medium.com/@zsh-eng/integrating-lsp-with-the-monaco-code-editor-b054e9b5421f)  
31. How to compile a Kotlin file to JavaScript? \- Stack Overflow, accessed December 19, 2025, [https://stackoverflow.com/questions/69428226/how-to-compile-a-kotlin-file-to-javascript](https://stackoverflow.com/questions/69428226/how-to-compile-a-kotlin-file-to-javascript)  
32. How are apps built using https://github.com/cashapp/zipline not violating Google play store policy? : r/Kotlin \- Reddit, accessed December 19, 2025, [https://www.reddit.com/r/Kotlin/comments/1fwcj03/how\_are\_apps\_built\_using/](https://www.reddit.com/r/Kotlin/comments/1fwcj03/how_are_apps_built_using/)  
33. Bringing Kotlin to the Web \- Google Developers Blog, accessed December 19, 2025, [https://developers.googleblog.com/bringing-kotlin-to-the-web/](https://developers.googleblog.com/bringing-kotlin-to-the-web/)