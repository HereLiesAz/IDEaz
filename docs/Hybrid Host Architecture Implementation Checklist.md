# **Hybrid Host Architecture for IDEaz: A Research Report on Manual Implementation of Redwood and Zipline**

## **1\. Introduction**

The evolution of mobile development environments has historically oscillated between rigid, monolithic native toolchains and flexible, dynamic web-based frameworks. The **IDEaz** project represents a paradigm shift in this landscape, positioning itself as a "Post-Code" creation engine that runs entirely on an Android device.1 The core philosophy of IDEaz dictates that the user should interact primarily with a live, running application rather than static source text, necessitating a build system that is both instantaneous and invisible. However, the constraints of the Android operating system—specifically the resource overhead of running a full Gradle daemon—require a novel approach to application compilation and deployment. This report analyzes the technical feasibility and implementation strategy for a "Hybrid Host Architecture" utilizing Cash App’s **Redwood** and **Zipline** libraries, manually orchestrated without Gradle to fit the IDEaz "No-Gradle" philosophy.

The proposed architecture fundamentally bifurcates the user's application into two distinct execution environments: the **Host** and the **Guest**. The Host acts as a stable, native container providing the runtime infrastructure, access to platform APIs, and the rendering engine. The Guest contains the user's volatile business logic and interface definitions, which are compiled dynamically and injected into the Host. This separation enables the "Edit-Build-Run" loop to be short-circuited; instead of recompiling a multi-megabyte APK for every minor logic change (a process taking tens of seconds to minutes), the IDEaz system can recompile only the lightweight Guest logic into JavaScript or Bytecode (taking milliseconds) and hot-swap it into the running Host process.

To achieve this on-device without Gradle, we must dissect the build logic typically encapsulated in the app.cash.redwood and app.cash.zipline Gradle plugins. We must then reconstruct this logic using lower-level tools already present or compatible with the IDEaz environment: the Kotlin compiler (kotlinc) via kotlin-compiler-embeddable, the Maven Resolver (Aether) for dependency management, and direct JVM invocations for code generation tools. This report details the manual invocation of the redwood-tooling-codegen CLI, the configuration of the zipline-kotlin-plugin for the K2JS compiler, and the cryptographic generation of Zipline manifests using Ed25519 signatures, all within the context of the IDEaz BuildService.

## **2\. Architectural Analysis: The Treehouse Model**

### **2.1 The Hybrid Host Paradigm**

The Hybrid Host architecture leverages the "Treehouse" model pioneered by Cash App, which combines Redwood (a multiplatform UI toolkit) and Zipline (a JS engine for Kotlin). In traditional Android development, the UI and the logic are compiled together into a single classes.dex file. In the Treehouse model, these concerns are physically separated by a serialization boundary.2

The **Host** is the native Android shell. It is built using the standard Android toolchain (aapt2, d8, apksigner) and installed on the device. It contains the "Widget Implementations"—the actual Kotlin code that instantiates android.widget.Button or androidx.compose.material3.Text.2 Crucially, the Host also contains the ZiplineLoader, a component responsible for fetching, verifying, and executing code bundles from an external source.4

The **Guest** is the user's project logic. It defines *what* the UI should look like but does not render it. Instead, it interacts with "Widget Interfaces"—abstract definitions of UI components. When the Guest code runs (inside a QuickJS sandbox provided by Zipline), it generates a stream of protocol events (the Redwood Protocol) describing changes to the UI tree. These events are serialized to JSON (or a binary format), passed across the bridge to the Host, and replayed against the actual native views.2

This architecture aligns perfectly with the IDEaz "Race to Build" strategy.1 The "Local Build" described in the IDEaz blueprint—which currently attempts to compile a full APK—can be augmented with a "Local Patch" lane. Because the Guest code is essentially just JavaScript (compiled from Kotlin), the IDE can generate a new bundle in seconds and push it to the ZiplineLoader via a local file or socket, instantly updating the running app without the user needing to reinstall the APK.

### **2.2 The Redwood Protocol Bridge**

The linchpin of this architecture is the Redwood Protocol. It allows the Guest to remain agnostic of the underlying platform (Android View, Jetpack Compose, or even HTML DOM), while the Host remains agnostic of the specific business logic driving the UI. This decoupling is what allows IDEaz to offer "Post-Code" features. Because the UI is transmitted as a stream of data, the IDE can intercept this stream to reconstruct a visual representation of the widget tree for a drag-and-drop editor, mapping visual elements back to the exact line of code in the Guest that generated them.

The protocol involves two generated components:

* **Protocol Guest:** Runs inside Zipline. It implements the widget interfaces (e.g., Button) and records method calls (e.g., text("Submit"), onClick(lambda)).  
* **Protocol Host:** Runs in the Android app. It receives the recording, deserializes the arguments, and invokes the corresponding methods on the real UI widgets.5

Implementing this manually requires precise coordination of the code generation step. The redwood-tooling-codegen tool MUST be invoked with the exact same schema definition for both the Host and Guest builds to ensure the protocol IDs match. If the Guest speaks "Protocol v2" (e.g., Button has ID 1\) and the Host speaks "Protocol v1" (where Button has ID 2), the bridge will collapse. In a Gradle environment, the plugin ensures consistency. In IDEaz, the BuildService must strictly enforce version alignment.

### **2.3 Zipline as the Hypervisor**

Zipline is not merely a JavaScript engine; it is a managed runtime environment for Kotlin code. It handles the complex "bridging" of interfaces, allowing JavaScript code to call Kotlin functions on the Host and vice versa. This is achieved through a Kotlin Compiler Plugin that generates adapter classes (extending ZiplineService) during the compilation phase.6

For IDEaz, Zipline acts as a secure sandbox. User code, which may contain errors or infinite loops, runs isolated within the QuickJS instance. If the Guest crashes, Zipline catches the exception, and the Host remains stable. The IDE can then capture this error (via Zipline's EventListener) and feed it into the AI Agent (Jules) for automated debugging 7, fulfilling the "Self-Healing" requirement of the IDEaz blueprint.1

Moreover, Zipline supports "Hot Reloading" by design. The ZiplineLoader can be configured to poll a URL (or watch a file path) for updates. When a new manifest is detected, it seamlessly loads the new code and restarts the Zipline services, preserving the "Post-Code" flow where changes feel instantaneous.8

## ---

**3\. Technical Deep Dive: Redwood Manual Code Generation**

The first critical step in the "No-Gradle" pipeline is generating the code required to bridge the Host and Guest. Redwood provides a command-line tool, redwood-tooling-codegen, which is normally wrapped by the Gradle plugin. We must execute this tool directly via the JVM.

### **3.1 Resolving the Codegen Artifact**

The code generator is distributed as a JAR file on Maven Central. The coordinate follows the pattern app.cash.redwood:redwood-tooling-codegen:\<version\>. The IDEaz application already includes the Aether Maven Resolver (libs.maven.resolver.\*) in its dependencies.1 The BuildService must utilizing the HttpDependencyResolver to download this JAR and its transitive dependencies (which include kotlinpoet, clikt, etc.) to a local cache directory (e.g., filesDir/tools/codegen/).

### **3.2 Constructing the Classpath**

Unlike a standalone executable, the codegen JAR likely relies on other libraries. To invoke it, we must construct a classpath string that includes the codegen JAR and all its dependencies.

* **Main Class:** Research indicates the entry point is app.cash.redwood.tooling.codegen.Main.5  
* **Execution Method:** Since IDEaz bundles a libopenjdk.so or relies on the Android runtime's dalvikvm (if compatible with the bytecode version), we effectively need to spawn a Java process.  
  Bash  
  /system/bin/java \-cp \<classpath\> app.cash.redwood.tooling.codegen.Main...

  However, on Android, spawning java commands is restricted. A more viable approach for IDEaz—which already embeds kotlin-compiler-embeddable—is to load the codegen classes dynamically using a DexClassLoader (if they are dexed) or simply run them if they are compatible with the Android runtime. Given that redwood-tooling-codegen is a JVM program, it might require desugaring (D8) before it can run on Android. Alternatively, and more likely for the IDEaz "Hybrid" model, this generation step might be the one place where we rely on the libopenjdk environment setup by setup\_env.sh 1, or strictly use the internal logic if the codegen tool is pure Kotlin/Java compatible with Android's ART.

### **3.3 CLI Arguments and Flags**

The arguments for the codegen tool have evolved significantly. Based on recent changelogs 5, the monolithic generation flags have been deprecated in favor of granular flags that separate Host and Guest generation. This is crucial for our Hybrid architecture where these live in different builds.

**Table 1: Redwood Codegen CLI Arguments (v0.11.0+)**

| Argument | Description | Required Context |
| :---- | :---- | :---- |
| \--schema | The fully qualified class name of the Schema interface (e.g., com.example.schema.MyDesignSystem). | All |
| \--out | The output directory for the generated Kotlin files. | All |
| \--protocol-guest | Generates the Guest-side protocol code (serialization logic). | Guest Build |
| \--protocol-host | Generates the Host-side protocol code (deserialization/widget binding). | Host Build |
| \--compose | Generates the Jetpack Compose implementations for the widgets (the Composable functions used by the user). | Guest Build |
| \--widget | Generates the Widget interfaces that the Host must implement. | Host Build |
| \--layout-modifiers | (Newer versions) Generates modifier interfaces if defined in the schema.10 | Both |

**Hypothetical Invocation for Guest Build:**

Bash

java app.cash.redwood.tooling.codegen.Main \\  
  \--schema com.example.schema.MyDesignSystem \\  
  \--out /data/data/com.hereliesaz.ideaz/files/projects/MyProject/build/generated/guest \\  
  \--protocol-guest \\  
  \--compose

**Hypothetical Invocation for Host Build:**

Bash

java app.cash.redwood.tooling.codegen.Main \\  
  \--schema com.example.schema.MyDesignSystem \\  
  \--out /data/data/com.hereliesaz.ideaz/files/projects/MyProject/build/generated/host \\  
  \--protocol-host \\  
  \--widget

### **3.4 Schema Parsing and Validation**

The code generator needs to read the user's schema definition. Historically, Redwood used a compiled classloader or KSP (Kotlin Symbol Processing). Recent updates 5 suggest a move towards a "source-based schema parser" as the default. This is a massive advantage for IDEaz. It means we **do not** need to compile the Schema.kt file into a class before running codegen. We can point the codegen tool at the source files.

**Implication:** This breaks a potential circular dependency in the build pipeline. The pipeline becomes:

1. Parse Schema.kt source.  
2. Generate Host/Guest Kotlin files.  
3. Compile Host (with generated widgets) \-\> APK.  
4. Compile Guest (with generated composables) \-\> JS.

## ---

**4\. Technical Deep Dive: Zipline Manual Compilation**

Zipline integration is the most complex part of the manual pipeline because it requires intercepting the Kotlin compiler to inject a plugin, and then managing the compilation of Kotlin IR to JavaScript.

### **4.1 The Zipline Compiler Plugin**

Zipline uses a compiler plugin (zipline-kotlin-plugin) to perform AST transformations. Specifically, it rewrites calls to Zipline.bind() and Zipline.take() to use generated adapter classes that handle serialization across the JS bridge.11 Without this plugin, the bridging mechanism fails at runtime.

#### **4.1.1 Locating the Plugin**

IDEaz must download the zipline-kotlin-plugin-embeddable.jar. This is distinct from the runtime library. The version must match the Kotlin compiler version used by IDEaz (libs.kotlin.compiler.embeddable).5 If IDEaz uses Kotlin 1.9.20, the Zipline plugin must be the version compiled against 1.9.20. Mismatches cause immediate crashes (NoSuchMethodError in the compiler).

#### **4.1.2 Injecting into Kotlinc**

To run the compiler plugin manually, we use the standard \-Xplugin flag of the Kotlin compiler CLI (K2JSCompiler or K2JVMCompiler). We also need to pass configuration options using the \-P flag.

**Compiler Argument Construction:**

Bash

\-Xplugin=/path/to/zipline-kotlin-plugin-embeddable.jar  
\-P plugin:app.cash.zipline:zipline-api-validation=enabled

The zipline-api-validation=enabled flag is crucial. It forces the compiler to check that all interfaces used in bind/take extend ZiplineService and use supported types. This provides build-time safety for the user's code, which is essential in a "Post-Code" environment where the user relies on the AI to get things right.12

### **4.2 Compiling Kotlin to JavaScript (IR Backend)**

Zipline strictly requires the Kotlin JS IR (Intermediate Representation) backend. The legacy JS backend is not supported.13

Invoking K2JSCompiler:  
IDEaz's BuildService must instantiate org.jetbrains.kotlin.cli.js.K2JSCompiler and call its exec method. The arguments are critical:  
**Table 2: K2JSCompiler Arguments for Zipline**

| Argument | Value | Reason |
| :---- | :---- | :---- |
| \-Xir-produce-js | N/A | Forces the IR backend.14 |
| \-Xir-per-module | N/A | Compiles each module to a separate JS file/fragment. Essential for Zipline's incremental loading and caching.14 |
| \-libraries | path/to/stdlib-js.klib, path/to/zipline.klib,... | The classpath for JS compilation. Must include the Zipline runtime and standard library. |
| \-module-kind | commonjs or umd | Zipline typically expects CommonJS or UMD modules to resolve dependencies between the app and the runtime.15 |
| \-output | .../build/guest/app.js | The target output file. |

The "No-Webpack" Challenge:  
Standard Zipline builds use Gradle and Webpack to bundle the JS. IDEaz has no Webpack. However, Zipline's ZiplineLoader is designed to load modules individually if they are defined in the manifest. By using \-Xir-per-module, we generate separate JS artifacts for the user's code and its dependencies. We do not need to bundle them into a single app.js. We just need to list them all in the manifest. This allows us to skip the complex bundling step entirely, simplifying the "No-Gradle" architecture.8

### **4.3 QuickJS Bytecode vs. Raw JS**

Zipline can execute either raw JavaScript or pre-compiled QuickJS bytecode (.zipline files).

* **Bytecode:** Loads faster, harder to reverse engineer. Requires qjsc (QuickJS Compiler).  
* **Raw JS:** Easier to debug, no extra native tool needed for compilation.

Constraint Analysis: qjsc is a native binary. While IDEaz bundles libquickjs.so for the runtime 1, it does not necessarily have the compiler binary executable on the device shell.  
Decision: For the on-device build pipeline, we should default to Raw JS. Zipline supports this transparently.8 It significantly reduces build time (skipping the bytecode compilation step) which is perfect for the "Hot Reload" loop. Bytecode optimization can be reserved for the "Remote Build" performed by GitHub Actions.

## ---

**5\. Technical Deep Dive: Secure Manifest Generation**

The manifest.zipline.json is the contract between the Build Service and the Runtime Loader. It lists every file the app needs, its download URL, and its cryptographic signature.

### **5.1 Anatomy of manifest.zipline.json**

The manifest structure is defined in the ZiplineManifest class. Based on the JSON samples found in the research 6, the schema is as follows:

JSON

{  
  "unsigned": {  
    "baseUrl": "file:///data/user/0/com.hereliesaz.ideaz/files/projects/MyProject/build/guest/",  
    "modules": {  
      "app-module": {  
        "url": "app-module.js",  
        "sha256": "a3f89...12b",  
        "dependsOnIds": \["kotlin-stdlib", "zipline", "redwood-runtime"\]  
      },  
      "kotlin-stdlib": {  
        "url": "kotlin-stdlib.js",  
        "sha256": "b4e12...99a"  
      }  
    },  
    "version": "1.0.0"  
  },  
  "signatures": {  
    "key1": "3045022100..."  
  }  
}

### **5.2 Manual Generation Logic**

Since we lack the Gradle task that automatically generates this, we must implement a ZiplineManifestGenerator class in Kotlin within the buildlogic package.

**Algorithm:**

1. **Input:** Directory of compiled JS files (from K2JSCompiler step).  
2. **Dependency Resolution:** Parse the JS files or KLIB manifests to determine the dependency graph (e.g., app-module imports zipline). This might be complex to parse manually.  
   * *Simplification:* For the MVP "Hybrid Host", we can assume a flat list of known dependencies (Standard Lib, Zipline, Redwood) plus the User Module. We map them all in the manifest.  
3. **Hashing:** Iterate over every .js file. Read bytes. Compute SHA-256. Hex encode.  
   Kotlin  
   fun sha256(file: File): String {  
       val digest \= MessageDigest.getInstance("SHA-256")  
       file.inputStream().use { input \-\>  
           val buffer \= ByteArray(8192)  
           var bytesRead: Int  
           while (input.read(buffer).also { bytesRead \= it }\!= \-1) {  
               digest.update(buffer, 0, bytesRead)  
           }  
       }  
       return digest.digest().joinToString("") { "%02x".format(it) }  
   }

4. **JSON Construction:** Assemble the unsigned object.

### **5.3 Cryptographic Signing**

Zipline strictly enforces signature verification unless explicitly disabled (which is unsafe). We must sign the manifest using **Ed25519** keys.

**Infrastructure:**

* **Key Storage:** The SettingsViewModel already holds the keys.1  
* **Library:** IDEaz includes libs.lazysodium.android (libsodium bindings) 1, which supports Ed25519.

**Signing Procedure:**

1. **Canonicalization:** The unsigned JSON object must be serialized to bytes. *Crucial:* The serialization order of keys matters for the signature verification. Zipline likely uses a canonical JSON form. We must ensure our manual JSON generator produces deterministic output (e.g., sorted keys).  
2. **Sign:**  
   Kotlin  
   val lazySodium \= LazySodiumAndroid(SodiumAndroid())  
   val signatureBytes \= ByteArray(64)  
   val manifestBytes \= unsignedJsonString.toByteArray(Charsets.UTF\_8)  
   val privateKeyBytes \= hexToBytes(settingsViewModel.getSigningKey())

   lazySodium.cryptoSignDetached(signatureBytes, manifestBytes, manifestBytes.size.toLong(), privateKeyBytes)  
   val signatureHex \= bytesToHex(signatureBytes)

3. **Append:** Add signatures: { "keyId": signatureHex } to the final JSON.

## ---

**6\. Implementation Strategy: The HybridBuildService**

The existing BuildService in IDEaz is designed for APK generation. We propose augmenting it with a specialized pipeline for Hybrid execution.

### **6.1 Pipeline Workflow**

**Stage 1: Bootstrap (Once per project load)**

* **Action:** HttpDependencyResolver ensures redwood-codegen.jar and zipline-plugin.jar are cached.  
* **Action:** HttpDependencyResolver downloads the runtime .klib files for the Guest (Redwood, Zipline, Kotlin Stdlib).

**Stage 2: Schema Codegen (Trigger: Schema.kt change)**

* **Action:** Run redwood-tooling-codegen twice:  
  1. \--protocol-host \-\> src/main/kotlin/generated/host  
  2. \--protocol-guest \-\> src/main/kotlin/generated/guest  
* **Action:** Trigger Host Rebuild (Stage 3).

**Stage 3: Host Rebuild (Trigger: Codegen output change)**

* **Action:** Standard KotlincCompile (JVM) of MainActivity \+ Generated Host Code.  
* **Action:** D8 \-\> ApkBuilder \-\> Sign \-\> Install.  
* **Time:** \~30-60s. (Slow, but infrequent).

**Stage 4: Guest Logic Rebuild (Trigger: Any logic edit)**

* **Action:** K2JSCompiler compiles GuestApp.kt \+ Generated Guest Code.  
* **Flags:** \-Xplugin=zipline..., \-libraries=zipline.klib.  
* **Output:** guest.js.  
* **Action:** ZiplineManifestGenerator creates manifest.zipline.json (signed).  
* **Time:** \~1-3s. (Fast).

**Stage 5: Hot Reload**

* **Action:** IDEaz writes manifest.zipline.json to Context.filesDir/hosted/.  
* **Action:** IDEaz sends broadcast com.hereliesaz.ideaz.RELOAD\_ZIPLINE.  
* **Host:** MainActivity receives broadcast. Calls ziplineLoader.loadOnce("app", "file:///.../manifest.zipline.json").  
* **Result:** UI updates instantly without restarting the Activity.

### **6.2 The "SimpleJsBundler" Revival**

The snippets mention a SimpleJsBundler.kt that is currently "dead code".1 In this architecture, it becomes vital. While we *can* load modules individually, some Kotlin/JS output (especially with commonjs) might require simple glue code to define the require() function or bootstrap the module loading if QuickJS doesn't support the specific module format out of the box.

The SimpleJsBundler should be refactored to:

1. Read the manifest.zipline.json.  
2. Identify the entry point module (the user's app).  
3. Inject a small JS preamble that sets up the global environment (e.g., globalThis, setTimeout polyfills if Zipline doesn't provide them fully, though Zipline usually handles this).

## ---

**7\. Operational & Security Considerations**

### **7.1 Security Sandbox**

The Hybrid Architecture significantly improves the security posture of IDEaz.

* **Isolation:** User code runs in QuickJS. It cannot access java.io.File or Android APIs directly unless explicitly exposed via a Zipline Service interface.  
* **Verification:** The ManifestVerifier ensures that even if the storage is compromised, the Host will refuse to load unsigned code. This is critical for the "External Project" feature 1 where projects might be imported from shared storage.

### **7.2 Version Locking**

A major risk is ABI incompatibility between the Host's Zipline Runtime (compiled into the IDE APK) and the Guest's compiled code (compiled by the on-device toolchain).

* **Mitigation:** IDEaz must strictly enforce version pinning. The version.properties file 1 should dictate the version of Zipline/Redwood used for both the internal build AND the artifacts downloaded for user projects. The BuildService must verify that the downloaded zipline-kotlin-plugin matches the bundled runtime version.

### **7.3 Crash Reporting**

When the Guest code crashes, it throws a JS exception which propagates to Kotlin as a ZiplineException. The Host must catch this.

* **Strategy:** Wrap the ZiplineLoader call in a try-catch. On exception, serialize the stack trace and invoke CrashHandler.report() 1 but with a specific flag indicating a "Guest Logic Crash".  
* **AI Integration:** This stack trace is gold for Jules. It should be fed back into the chat context immediately: "Your code crashed with \[Error\]. Fix it."

## ---

**8\. Conclusion**

Implementing the Hybrid Host Architecture without Gradle is a formidable challenge that requires treating the Android device as a full-fledged build server. It necessitates dissecting the convenience layers of Gradle plugins and manually operating the gears of kotlinc, redwood-codegen, and cryptographic signing.

However, the payoff is substantial. By decoupling the heavy Native Host from the lightweight Guest Logic, IDEaz can achieve the "Post-Code" dream: a development environment where changes are reflected instantly, the boundary between "using" and "building" blurs, and the AI agent can iterate on logic loops in milliseconds rather than minutes. The research confirms that all necessary components (Aether, LazySodium, Kotlin Embeddable) are present in the dependency tree; the remaining task is the orchestration logic within BuildService.

#### **Works cited**

1. project\_backup\_full\_2025-12-19\_20-14-27.txt  
2. Redwood \+ Treehouse: Server Driven UI without sacrifices | by Santiago Mattiauda, accessed December 20, 2025, [https://medium.com/@santimattius/redwood-treehouse-server-driven-ui-without-sacrifices-3f34ca14b45e](https://medium.com/@santimattius/redwood-treehouse-server-driven-ui-without-sacrifices-3f34ca14b45e)  
3. Playing in the Treehouse with Redwood and Zipline by Jake Wharton \- YouTube, accessed December 20, 2025, [https://www.youtube.com/watch?v=G4LK\_euTadU](https://www.youtube.com/watch?v=G4LK_euTadU)  
4. Dynamic Code with Zipline | Public Object, accessed December 20, 2025, [https://cdn.publicobject.com/20220901-dynamic-code-with-zipline.pdf](https://cdn.publicobject.com/20220901-dynamic-code-with-zipline.pdf)  
5. redwood/CHANGELOG.md at trunk \- GitHub, accessed December 20, 2025, [https://github.com/cashapp/redwood/blob/trunk/CHANGELOG.md](https://github.com/cashapp/redwood/blob/trunk/CHANGELOG.md)  
6. cashapp/zipline: Run Kotlin/JS libraries in Kotlin/JVM and Kotlin/Native programs \- GitHub, accessed December 20, 2025, [https://github.com/cashapp/zipline](https://github.com/cashapp/zipline)  
7. I have got \`Zipline\` to implement a plugin system Most of th kotlinlang \#squarelibraries, accessed December 20, 2025, [https://slack-chats.kotlinlang.org/t/13884026/i-have-got-zipline-to-implement-a-plugin-system-most-of-the-](https://slack-chats.kotlinlang.org/t/13884026/i-have-got-zipline-to-implement-a-plugin-system-most-of-the-)  
8. How We Sped Up Zipline Hot Reload | Cash App Code Blog, accessed December 20, 2025, [https://code.cash.app/how-we-sped-up-zipline-hot-reload](https://code.cash.app/how-we-sped-up-zipline-hot-reload)  
9. swagger-codegen is not correctly generating common parameters for any language, accessed December 20, 2025, [https://stackoverflow.com/questions/63222296/swagger-codegen-is-not-correctly-generating-common-parameters-for-any-language](https://stackoverflow.com/questions/63222296/swagger-codegen-is-not-correctly-generating-common-parameters-for-any-language)  
10. Releases · cashapp/redwood \- GitHub, accessed December 20, 2025, [https://github.com/cashapp/redwood/releases](https://github.com/cashapp/redwood/releases)  
11. Download Native Code in your Mobile Applications with Zipline | by Santiago Mattiauda, accessed December 20, 2025, [https://medium.com/@santimattius/download-native-code-in-your-mobile-applications-with-zipline-50dc83b581b7](https://medium.com/@santimattius/download-native-code-in-your-mobile-applications-with-zipline-50dc83b581b7)  
12. Kotlin K2 FIR Example \- GitHub Gist, accessed December 20, 2025, [https://gist.github.com/handstandsam/9a561fc78b593039d1dd500fae14b355](https://gist.github.com/handstandsam/9a561fc78b593039d1dd500fae14b355)  
13. zipline/CHANGELOG.md at trunk \- GitHub, accessed December 20, 2025, [https://github.com/cashapp/quickjs-java/blob/trunk/CHANGELOG.md](https://github.com/cashapp/quickjs-java/blob/trunk/CHANGELOG.md)  
14. Kotlin/JS compiler features, accessed December 20, 2025, [https://kotlinlang.org/docs/js-ir-compiler.html](https://kotlinlang.org/docs/js-ir-compiler.html)  
15. JavaScript modules | Kotlin Documentation, accessed December 20, 2025, [https://kotlinlang.org/docs/js-modules.html](https://kotlinlang.org/docs/js-modules.html)