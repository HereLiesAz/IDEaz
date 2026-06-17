package com.hereliesaz.ideaz.ai.local

import android.content.Context
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Thrown when a runtime's backend library isn't present in this build yet. */
class LocalRuntimeUnavailableException(runtimeName: String) :
    Exception("On-device runtime \"$runtimeName\" is not available in this build yet.")

/**
 * A pluggable on-device inference backend (MediaPipe LLM Inference, llama.cpp,
 * ONNX Runtime GenAI, …).
 *
 * Concrete runtimes are detected by reflection so the app builds and runs without
 * their (large, native) dependencies. A runtime becomes usable once its dependency
 * is added to the build and its [generate] is implemented — done per-runtime in
 * follow-up changes so each can be verified on a device independently.
 */
interface LocalModelRuntime {
    val id: String
    val displayName: String

    /** True if this backend's library is present in the current build. */
    fun isAvailable(context: Context): Boolean

    /** One-shot generation for [prompt] using the model file at [modelFile]. */
    suspend fun generate(context: Context, modelFile: File, prompt: String): String
}

internal fun classPresent(fqcn: String): Boolean =
    runCatching { Class.forName(fqcn, false, LocalModelRuntime::class.java.classLoader) }.isSuccess

/** MediaPipe LLM Inference — Gemma/Phi/Falcon `.task` models. */
object MediaPipeRuntime : LocalModelRuntime {
    override val id = "mediapipe"
    override val displayName = "MediaPipe LLM Inference"
    override fun isAvailable(context: Context) =
        classPresent("com.google.mediapipe.tasks.genai.llminference.LlmInference")

    override suspend fun generate(context: Context, modelFile: File, prompt: String): String =
        withContext(Dispatchers.IO) {
            // Loads the model and runs a one-shot generation. (Per-call load is
            // simple and correct; caching the engine across calls is a future
            // optimization but needs careful native-resource lifecycle handling.)
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .build()
            LlmInference.createFromOptions(context.applicationContext, options).use { llm ->
                llm.generateResponse(prompt)
            }
        }
}

/**
 * llama.cpp — any GGUF model, via the `android.llama.cpp.LLamaAndroid` JNI binding
 * in the `:llama-cpp-module` NDK module. llama.cpp has no published Android Maven
 * artifact, so the module is included only when its `llama.cpp` submodule is
 * present (see its README). [generate] talks to the binding by reflection so the
 * app compiles whether or not the module is on the classpath; when it's absent
 * [isAvailable] is false and the model is hidden.
 */
object LlamaCppRuntime : LocalModelRuntime {
    override val id = "llamacpp"
    override val displayName = "llama.cpp (GGUF)"

    // LLamaAndroid is a process-global singleton (one native model at a time), so
    // serialize whole load→complete→unload cycles; concurrent generate() calls would
    // otherwise corrupt state (e.g. unload while another is mid-complete).
    private val mutex = Mutex()

    override fun isAvailable(context: Context) =
        classPresent("android.llama.cpp.LLamaAndroid")

    override suspend fun generate(context: Context, modelFile: File, prompt: String): String =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val cls = runCatching { Class.forName("android.llama.cpp.LLamaAndroid") }.getOrNull()
                    ?: throw LocalRuntimeUnavailableException(displayName)
                // instance() → load(path) → complete(prompt, maxTokens) → unload().
                val instance = cls.getMethod("instance").invoke(null)
                cls.getMethod("load", String::class.java).invoke(instance, modelFile.absolutePath)
                try {
                    cls.getMethod("complete", String::class.java, Int::class.javaPrimitiveType)
                        .invoke(instance, prompt, 512) as String
                } finally {
                    runCatching { cls.getMethod("unload").invoke(instance) }
                }
            }
        }
}

/**
 * ONNX Runtime GenAI — Phi-3/3.5, Qwen (a multi-file model *directory*). Enable by
 * adding the `com.microsoft.onnxruntime:onnxruntime-genai-android` AAR (see
 * `docs/on-device-runtimes.md`). [generate] drives `ai.onnxruntime.genai`
 * (Model/Tokenizer/Generator) over the downloaded model directory ([modelFile]'s
 * parent) by reflection, so the app compiles without the AAR; when it's absent
 * [isAvailable] is false and the model is hidden.
 */
object OnnxGenAiRuntime : LocalModelRuntime {
    override val id = "onnx"
    override val displayName = "ONNX Runtime GenAI"
    override fun isAvailable(context: Context) =
        classPresent("ai.onnxruntime.genai.Model")

    override suspend fun generate(context: Context, modelFile: File, prompt: String): String =
        withContext(Dispatchers.IO) {
            val pkg = "ai.onnxruntime.genai"
            val modelCls = runCatching { Class.forName("$pkg.Model") }.getOrNull()
                ?: throw LocalRuntimeUnavailableException(displayName)
            val tokenizerCls = Class.forName("$pkg.Tokenizer")
            val paramsCls = Class.forName("$pkg.GeneratorParams")
            val generatorCls = Class.forName("$pkg.Generator")
            val sequencesCls = Class.forName("$pkg.Sequences")

            // GenAI loads a directory (model + genai_config.json + tokenizer files).
            // Model/Tokenizer/Sequences/GeneratorParams/Generator all wrap native
            // memory (AutoCloseable); close every one to avoid native leaks/OOM.
            val dir = (modelFile.parentFile ?: modelFile).absolutePath
            val model = modelCls.getConstructor(String::class.java).newInstance(dir)
            var tokenizer: Any? = null
            var encoded: Any? = null
            var params: Any? = null
            var generator: Any? = null
            try {
                tokenizer = tokenizerCls.getConstructor(modelCls).newInstance(model)
                encoded = tokenizerCls.getMethod("encode", String::class.java).invoke(tokenizer, prompt)
                params = paramsCls.getConstructor(modelCls).newInstance(model)
                paramsCls.getMethod("setSearchOption", String::class.java, Double::class.javaPrimitiveType)
                    .invoke(params, "max_length", 1024.0)
                generator = generatorCls.getConstructor(modelCls, paramsCls).newInstance(model, params)
                // Feed prompt tokens. Newer API: Generator.appendTokenSequences(Sequences);
                // older API: GeneratorParams.setInput(Sequences) before generation.
                runCatching {
                    generatorCls.getMethod("appendTokenSequences", sequencesCls).invoke(generator, encoded)
                }.onFailure {
                    runCatching {
                        paramsCls.getMethod("setInput", sequencesCls).invoke(params, encoded)
                    }
                }
                val isDone = generatorCls.getMethod("isDone")
                val computeLogits = runCatching { generatorCls.getMethod("computeLogits") }.getOrNull()
                val generateNextToken = generatorCls.getMethod("generateNextToken")
                val getSequence = generatorCls.getMethod("getSequence", Long::class.javaPrimitiveType)
                var guard = 0
                while (isDone.invoke(generator) == false && guard < 4096) {
                    computeLogits?.invoke(generator)
                    generateNextToken.invoke(generator)
                    guard++
                }
                val outIds = getSequence.invoke(generator, 0L) as IntArray
                tokenizerCls.getMethod("decode", IntArray::class.java).invoke(tokenizer, outIds) as String
            } finally {
                runCatching { (generator as? AutoCloseable)?.close() }
                runCatching { (params as? AutoCloseable)?.close() }
                runCatching { (encoded as? AutoCloseable)?.close() }
                runCatching { (tokenizer as? AutoCloseable)?.close() }
                runCatching { (model as? AutoCloseable)?.close() }
            }
        }
}

/**
 * AICore — the device's system-managed Gemini Nano service
 * (`com.google.ai.edge.aicore`). Unlike the file-based runtimes, the model is
 * provided and updated by the system (no app download). Available on supported
 * hardware (Pixel 8+/select Samsung); [generate] surfaces a clear message on
 * unsupported devices. This is the same engine the GEMINI_NANO provider uses,
 * exposed here so it appears in the unified on-device picker.
 */
object AiCoreRuntime : LocalModelRuntime {
    override val id = "aicore"
    override val displayName = "AICore · Gemini Nano (system)"
    override fun isAvailable(context: Context) =
        classPresent("com.google.ai.edge.aicore.GenerativeModel")
    override suspend fun generate(context: Context, modelFile: File, prompt: String): String {
        val config = generationConfig {
            this.context = context.applicationContext
            temperature = 0.2f
            topK = 16
            candidateCount = 1
            maxOutputTokens = 512
        }
        val model = GenerativeModel(generationConfig = config)
        return try {
            model.prepareInferenceEngine()
            model.generateContent(prompt).text.orEmpty().ifBlank { "(no text)" }
        } finally {
            runCatching { model.close() }
        }
    }
}

/** Registry of known on-device runtimes. */
object LocalModelRuntimes {
    val all: List<LocalModelRuntime> =
        listOf(AiCoreRuntime, MediaPipeRuntime, LlamaCppRuntime, OnnxGenAiRuntime)
    fun byId(id: String): LocalModelRuntime? = all.firstOrNull { it.id == id }
}
