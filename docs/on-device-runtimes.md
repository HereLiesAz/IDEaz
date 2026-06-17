# On-device LLM runtimes — integration guide

IDEaz ships a pluggable on-device model framework (`ai/local/`): a
`LocalModelRuntime` registry, a multi-file `ModelDownloadManager`, a catalog, the
`LocalLlmAdapter`, and the **On-device Models** Settings section.

Working today (dependency in the build):
- **AICore (Gemini Nano)** — `AiCoreRuntime`, system-managed, no download.
- **MediaPipe LLM Inference** — `MediaPipeRuntime`, wired (`com.google.mediapipe:tasks-genai`).

**ONNX Runtime GenAI** (`OnnxGenAiRuntime`) and **llama.cpp** (`LlamaCppRuntime`)
now have their `generate()` **implemented** (against `ai.onnxruntime.genai` and
`android.llama.cpp.LLamaAndroid` respectively, via reflection so the app compiles
without the heavy native deps). They stay dormant until their library is on the
classpath: `isAvailable()` is false, and the **On-device Models** picker hides any
model whose backend isn't actually usable on the device (it also filters by RAM,
CPU ABI, and required tokens — see `LocalModelAvailability`). Add the library
(below) and the matching models appear automatically.

---

## ONNX Runtime GenAI

**Why it isn't wired yet:** `com.microsoft.onnxruntime:onnxruntime-genai-android`
does **not** resolve from Maven Central (tried 0.5.2 and 0.6.0). Microsoft ships
the GenAI **Android AAR via GitHub releases**, not Maven Central. So it must be
vendored (or you must supply the exact Maven coordinates if a mirror publishes it).

### 1. Vendor the AAR
Download `onnxruntime-genai-android-<ver>.aar` from
https://github.com/microsoft/onnxruntime-genai/releases into `app/libs/`, then in
`app/build.gradle.kts`:
```kotlin
implementation(files("libs/onnxruntime-genai-android-<ver>.aar"))
// GenAI depends on onnxruntime-android at runtime:
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
```

### 2. `OnnxGenAiRuntime.generate()` is already implemented
It's in `ai/local/LocalModelRuntime.kt`, driving `ai.onnxruntime.genai`
(Model/Tokenizer/GeneratorParams/Generator) **by reflection** over the model
directory (`modelFile.parentFile`). Once the AAR is on the classpath it activates
automatically. If the vendored version's API differs from the reflected calls
(method names/signatures drift across releases), adjust them there. For reference,
the equivalent typed code is:
```kotlin
override suspend fun generate(context: Context, modelFile: File, prompt: String): String =
    withContext(Dispatchers.IO) {
        val modelDir = modelFile.parentFile!!.absolutePath
        ai.onnxruntime.genai.Model(modelDir).use { model ->
            ai.onnxruntime.genai.Tokenizer(model).use { tok ->
                val sequences = tok.encode(prompt)
                ai.onnxruntime.genai.GeneratorParams(model).use { params ->
                    params.setSearchOption("max_length", 1024.0)
                    ai.onnxruntime.genai.Generator(model, params).use { gen ->
                        gen.appendTokenSequences(sequences)
                        val out = StringBuilder()
                        ai.onnxruntime.genai.TokenizerStream(tok).use { stream ->
                            while (!gen.isDone) {
                                gen.generateNextToken()
                                out.append(stream.decode(gen.getLastTokenInSequence(0)))
                            }
                        }
                        out.toString()
                    }
                }
            }
        }
    }
```
> The exact `ai.onnxruntime.genai` API varies across versions — confirm method
> names against the AAR you vendored (`javap` the bundled `classes.jar`).

### 3. R8 (`proguard-rules.pro`)
Keep rules for `ai.onnxruntime.genai.**` (and `android.llama.cpp.**`) are already
in `proguard-rules.pro`. If you vendor the broader `ai.onnxruntime.**` runtime, add:
```
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
```

### 4. Catalog
The `phi3_5-mini-onnx` entry in `LocalModelCatalog` lists the model directory's
files — **verify the file list/URLs** against the model card before shipping.

---

## llama.cpp (GGUF)

**Why it isn't wired yet:** llama.cpp has **no published Android Maven artifact**.
It needs an NDK module that builds `libllama` and exposes a JNI binding. A scaffold
is provided in `/llama-cpp-module/` — see its `README.md`. Summary:

1. Add llama.cpp as a submodule inside the module:
   `git submodule add https://github.com/ggml-org/llama.cpp llama-cpp-module/llama.cpp`

That's the only manual step. `settings.gradle.kts` and `app/build.gradle.kts` now
**conditionally** include/depend on `:llama-cpp-module` when that submodule
directory exists, so a plain checkout still builds without the NDK and the module
activates automatically once the submodule is present. `LlamaCppRuntime.generate()`
is already implemented (it calls `android.llama.cpp.LLamaAndroid` by reflection),
`isAvailable()` already detects the binding, and the R8 keep rule
(`-keep class android.llama.cpp.** { *; }`) is in `proguard-rules.pro`.

> The C API in `llama-android.cpp` targets a recent llama.cpp release; pin a
> known-good tag and adjust if the API drifted. None of this can be compile- or
> run-verified without the submodule + NDK toolchain.
