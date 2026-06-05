package com.hereliesaz.ideaz.ai.local

import android.content.Context
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
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

/** llama.cpp — any GGUF model. Wired in a follow-up. */
object LlamaCppRuntime : LocalModelRuntime {
    override val id = "llamacpp"
    override val displayName = "llama.cpp (GGUF)"
    override fun isAvailable(context: Context) =
        classPresent("android.llama.cpp.LLamaAndroid") || classPresent("de.kherud.llama.LlamaModel")
    override suspend fun generate(context: Context, modelFile: File, prompt: String): String =
        throw LocalRuntimeUnavailableException(displayName)
}

/** ONNX Runtime GenAI — Phi-3/3.5, Qwen. Wired in a follow-up. */
object OnnxGenAiRuntime : LocalModelRuntime {
    override val id = "onnx"
    override val displayName = "ONNX Runtime GenAI"
    override fun isAvailable(context: Context) =
        classPresent("ai.onnxruntime.genai.Model")
    override suspend fun generate(context: Context, modelFile: File, prompt: String): String =
        throw LocalRuntimeUnavailableException(displayName)
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
