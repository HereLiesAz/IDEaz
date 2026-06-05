# On-device LLM runtimes — integration guide

IDEaz ships a pluggable on-device model framework (`ai/local/`): a
`LocalModelRuntime` registry, a multi-file `ModelDownloadManager`, a catalog, the
`LocalLlmAdapter`, and the **On-device Models** Settings section.

Working today:
- **AICore (Gemini Nano)** — `AiCoreRuntime`, system-managed, no download.
- **MediaPipe LLM Inference** — `MediaPipeRuntime`, wired (`com.google.mediapipe:tasks-genai`).

Pending their native backends (this guide): **ONNX Runtime GenAI** and **llama.cpp**.
Both are registered runtimes that report "backend not in this build" until wired,
so the picker degrades cleanly.

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

### 2. Implement `OnnxGenAiRuntime.generate()` (in `ai/local/LocalModelRuntime.kt`)
The model is a **directory** — our `ModelDownloadManager` already downloads all
files into one (`additionalFiles`), so point the runtime at the parent dir:
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
2. Include the module: in `settings.gradle.kts` add `include(":llama-cpp-module")`.
3. Depend on it: in `app/build.gradle.kts` add
   `implementation(project(":llama-cpp-module"))`.
4. Replace `LlamaCppRuntime.generate()` with a direct call to
   `android.llama.cpp.LLamaAndroid` (the scaffold's binding), e.g.:
```kotlin
override suspend fun generate(context: Context, modelFile: File, prompt: String): String {
    val llm = android.llama.cpp.LLamaAndroid.instance()
    llm.load(modelFile.absolutePath)
    return try { llm.complete(prompt, maxTokens = 512) } finally { llm.unload() }
}
```
5. R8 (`proguard-rules.pro`):
```
-keep class android.llama.cpp.** { *; }
```

`isAvailable()` already detects `android.llama.cpp.LLamaAndroid`, so the runtime
activates automatically once the module is on the classpath.
