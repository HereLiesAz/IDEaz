package com.hereliesaz.ideaz.ai.local

import android.content.Context
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    suspend fun generate(
        context: Context,
        modelFile: File,
        prompt: String,
        limits: LocalInferenceLimits,
    ): String

    /** Releases cached native/model resources after model changes or memory pressure. */
    suspend fun release()
}

/** Device-tier bounds shared by every local backend. */
data class LocalInferenceLimits(
    val maxPromptChars: Int,
    val maxContextTokens: Int,
    val maxOutputTokens: Int,
)

/**
 * Conservative bounds: four UTF-16 characters approximate one tokenizer token.
 * [LocalInferenceLimits.maxPromptChars] is sized to leave [LocalInferenceLimits.maxOutputTokens]
 * of headroom inside [LocalInferenceLimits.maxContextTokens] - it is the prompt-side
 * share of the *same* total budget, not an independent limit. A runtime whose API
 * takes a single total-token budget (MediaPipe's `setMaxTokens`, ONNX's `max_length`)
 * should be given [LocalInferenceLimits.maxContextTokens] directly.
 */
internal fun localInferenceLimits(totalRamBytes: Long): LocalInferenceLimits = when {
    totalRamBytes < 4_000_000_000L -> deviceTierLimits(maxContextTokens = 2_048, maxOutputTokens = 256)
    totalRamBytes in 4_000_000_000L until 8_000_000_000L -> deviceTierLimits(maxContextTokens = 4_096, maxOutputTokens = 512)
    else -> deviceTierLimits(maxContextTokens = 6_144, maxOutputTokens = 768)
}

private const val CHARS_PER_TOKEN = 4

private fun deviceTierLimits(maxContextTokens: Int, maxOutputTokens: Int): LocalInferenceLimits =
    LocalInferenceLimits(
        maxPromptChars = (maxContextTokens - maxOutputTokens) * CHARS_PER_TOKEN,
        maxContextTokens = maxContextTokens,
        maxOutputTokens = maxOutputTokens,
    )

internal fun classPresent(fqcn: String): Boolean =
    runCatching { Class.forName(fqcn, false, LocalModelRuntime::class.java.classLoader) }.isSuccess

/** MediaPipe LLM Inference — Gemma/Phi/Falcon `.task` models. */
@Suppress("DEPRECATION")
object MediaPipeRuntime : LocalModelRuntime {
    override val id = "mediapipe"
    override val displayName = "MediaPipe LLM Inference"
    override fun isAvailable(context: Context) =
        classPresent("com.google.mediapipe.tasks.genai.llminference.LlmInference")

    private val mutex = Mutex()
    private var cachedPath: String? = null
    private var cachedEngine: LlmInference? = null

    override suspend fun generate(
        context: Context,
        modelFile: File,
        prompt: String,
        limits: LocalInferenceLimits,
    ): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (cachedPath != modelFile.absolutePath || cachedEngine == null) {
                cachedEngine?.runCatching { close() }
                cachedEngine = null
                cachedPath = null
                // setMaxTokens is MediaPipe's *total* (prompt + output) budget, not an
                // output-only cap - passing maxOutputTokens here left far too little
                // room for the prompt (the tool-protocol instructions alone are a few
                // hundred tokens), so generation failed on essentially every real turn.
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(limits.maxContextTokens)
                    .build()
                cachedEngine = LlmInference.createFromOptions(context.applicationContext, options)
                cachedPath = modelFile.absolutePath
            }
            cachedEngine!!.generateResponse(prompt)
        }
    }

    override suspend fun release() = mutex.withLock {
        cachedEngine?.runCatching { close() }
        cachedEngine = null
        cachedPath = null
    }
}

/**
 * llama.cpp — any GGUF model, via the `android.llama.cpp.LLamaAndroid` JNI binding
 * in the `:llama-cpp-module` NDK module. llama.cpp has no published Android Maven
 * artifact, so the module is included only when its `llama.cpp` submodule is
 * present (see its README). [generate] talks to the binding by reflection so the
 * app compiles whether or not the module is on the classpath; when it's absent
 * [isAvailable] is false and the model is hidden.
 *
 * The binding's native `nativeComplete` sizes its own context as
 * `n_prompt + maxTokens + 8` (see `llama-android.cpp`) rather than taking a fixed
 * context-budget parameter, so [LocalInferenceLimits.maxContextTokens] isn't passed
 * here directly - it's honored indirectly: [limits] is used to bound the prompt
 * that's built *before* this is called (see `boundedLocalPrompt` in
 * `LocalLlmAdapter`), and [LocalInferenceLimits.maxPromptChars] already reserves
 * room for [LocalInferenceLimits.maxOutputTokens] within the same total budget.
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

    private var loadedPath: String? = null
    private var cachedInstance: Any? = null
    private var cachedClass: Class<*>? = null

    override suspend fun generate(
        context: Context,
        modelFile: File,
        prompt: String,
        limits: LocalInferenceLimits,
    ): String =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val cls = runCatching { Class.forName("android.llama.cpp.LLamaAndroid") }.getOrNull()
                    ?: throw LocalRuntimeUnavailableException(displayName)
                val instance = cls.getMethod("instance").invoke(null)
                if (loadedPath != modelFile.absolutePath) {
                    if (loadedPath != null) runCatching { cls.getMethod("unload").invoke(instance) }
                    loadedPath = null
                    cachedInstance = null
                    cachedClass = null
                    cls.getMethod("load", String::class.java).invoke(instance, modelFile.absolutePath)
                    loadedPath = modelFile.absolutePath
                    cachedInstance = instance
                    cachedClass = cls
                }
                cls.getMethod("complete", String::class.java, Int::class.javaPrimitiveType)
                    .invoke(instance, prompt, limits.maxOutputTokens) as String
            }
        }

    override suspend fun release() = mutex.withLock {
        cachedInstance?.let { instance ->
            cachedClass?.let { cls -> runCatching { cls.getMethod("unload").invoke(instance) } }
        }
        cachedInstance = null
        cachedClass = null
        loadedPath = null
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

    private val mutex = Mutex()
    private var cachedDirectory: String? = null
    private var cachedModel: Any? = null
    private var cachedTokenizer: Any? = null

    override suspend fun generate(
        context: Context,
        modelFile: File,
        prompt: String,
        limits: LocalInferenceLimits,
    ): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val pkg = "ai.onnxruntime.genai"
            val modelCls = runCatching { Class.forName("$pkg.Model") }.getOrNull()
                ?: throw LocalRuntimeUnavailableException(displayName)
            val tokenizerCls = Class.forName("$pkg.Tokenizer")
            val paramsCls = Class.forName("$pkg.GeneratorParams")
            val generatorCls = Class.forName("$pkg.Generator")
            val sequencesCls = Class.forName("$pkg.Sequences")

            // GenAI loads a directory (model + genai_config.json + tokenizer files).
            // Model/Tokenizer stay cached for this directory. Per-call sequences,
            // params, and generator wrap native memory and are always closed.
            val dir = (modelFile.parentFile ?: modelFile).absolutePath
            if (cachedDirectory != dir || cachedModel == null || cachedTokenizer == null) {
                closeCached()
                cachedModel = modelCls.getConstructor(String::class.java).newInstance(dir)
                cachedTokenizer = tokenizerCls.getConstructor(modelCls).newInstance(cachedModel)
                cachedDirectory = dir
            }
            val model = cachedModel!!
            val tokenizer = cachedTokenizer!!
            var encoded: Any? = null
            var params: Any? = null
            var generator: Any? = null
            try {
                encoded = tokenizerCls.getMethod("encode", String::class.java).invoke(tokenizer, prompt)
                params = paramsCls.getConstructor(modelCls).newInstance(model)
                paramsCls.getMethod("setSearchOption", String::class.java, Double::class.javaPrimitiveType)
                    .invoke(params, "max_length", limits.maxContextTokens.toDouble())
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
                while (isDone.invoke(generator) == false && guard < limits.maxOutputTokens) {
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
            }
        }
    }

    override suspend fun release() = mutex.withLock { closeCached() }

    private fun closeCached() {
        runCatching { (cachedTokenizer as? AutoCloseable)?.close() }
        runCatching { (cachedModel as? AutoCloseable)?.close() }
        cachedTokenizer = null
        cachedModel = null
        cachedDirectory = null
    }
}

/**
 * AICore — the device's system-managed Gemini Nano service
 * (`com.google.ai.edge.aicore`). Unlike the file-based runtimes, the model is
 * provided and updated by the system (no app download). Available on supported
 * hardware (Pixel 8+/select Samsung); [generate] surfaces a clear message on
 * unsupported devices. This is the same engine the GEMINI_NANO provider uses,
 * exposed here so it appears in the unified on-device picker.
 *
 * [GenerationConfig] exposes no total-context-budget setter - only
 * [LocalInferenceLimits.maxOutputTokens] is configurable here. The prompt side of
 * the same budget is still bounded by [LocalInferenceLimits.maxPromptChars] before
 * it reaches [generate] (see `boundedLocalPrompt` in `LocalLlmAdapter`).
 */
object AiCoreRuntime : LocalModelRuntime {
    override val id = "aicore"
    override val displayName = "AICore · Gemini Nano (system)"
    override fun isAvailable(context: Context) =
        classPresent("com.google.ai.edge.aicore.GenerativeModel")
    private val mutex = Mutex()
    private var cachedModel: GenerativeModel? = null
    private var cachedOutputLimit: Int? = null

    override suspend fun generate(
        context: Context,
        modelFile: File,
        prompt: String,
        limits: LocalInferenceLimits,
    ): String = mutex.withLock {
        if (cachedModel == null || cachedOutputLimit != limits.maxOutputTokens) {
            cachedModel?.runCatching { close() }
            cachedModel = null
            cachedOutputLimit = null
            val config = generationConfig {
                this.context = context.applicationContext
                temperature = 0.2f
                topK = 16
                candidateCount = 1
                maxOutputTokens = limits.maxOutputTokens
            }
            cachedModel = GenerativeModel(generationConfig = config).also { it.prepareInferenceEngine() }
            cachedOutputLimit = limits.maxOutputTokens
        }
        cachedModel!!.generateContent(prompt).text.orEmpty().ifBlank { "(no text)" }
    }

    override suspend fun release() = mutex.withLock {
        cachedModel?.runCatching { close() }
        cachedModel = null
        cachedOutputLimit = null
    }
}

/** Registry of known on-device runtimes. */
object LocalModelRuntimes {
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val all: List<LocalModelRuntime> =
        listOf(AiCoreRuntime, MediaPipeRuntime, LlamaCppRuntime, OnnxGenAiRuntime)
    fun byId(id: String): LocalModelRuntime? = all.firstOrNull { it.id == id }
    suspend fun releaseAll() {
        all.forEach { runtime ->
            try {
                runtime.release()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // One broken native backend must not retain every other engine.
            }
        }
    }
    fun requestReleaseAll() {
        releaseScope.launch { releaseAll() }
    }
}
