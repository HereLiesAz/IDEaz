package com.hereliesaz.ideaz.ai.local

/**
 * A downloadable on-device model.
 *
 * [requiresAuth] models (e.g. gated Gemma) need a provider token the user supplies
 * before the download will succeed. URLs are direct download links; the download
 * manager is URL-generic, so this catalog can grow without code changes.
 */
data class LocalModel(
    val id: String,
    val name: String,
    val runtimeId: String,
    val url: String,
    val approxSizeBytes: Long,
    val fileName: String,
    val requiresAuth: Boolean = false,
    /** True for runtimes that manage their own model (e.g. AICore): no file download. */
    val systemManaged: Boolean = false,
    val notes: String = "",
)

/**
 * Curated starter catalog spanning the supported runtimes. The exact URLs/filenames
 * should be verified before each is offered for download (some upstreams rename
 * quantizations); the architecture does not depend on any specific entry.
 */
object LocalModelCatalog {
    val models: List<LocalModel> = listOf(
        LocalModel(
            id = "aicore-gemini-nano",
            name = "Gemini Nano (AICore · system-managed)",
            runtimeId = "aicore",
            url = "",
            approxSizeBytes = 0,
            fileName = "",
            systemManaged = true,
            notes = "No download — provided and updated by the device's AICore service. " +
                "Supported hardware only (Pixel 8+/select Samsung).",
        ),
        LocalModel(
            id = "qwen2_5-0_5b-instruct-q4-gguf",
            name = "Qwen2.5 0.5B Instruct · Q4 (GGUF)",
            runtimeId = "llamacpp",
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            approxSizeBytes = 400_000_000,
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            notes = "Tiny and fast; good for low-RAM devices.",
        ),
        LocalModel(
            id = "gemma2-2b-it-q4-gguf",
            name = "Gemma 2 2B Instruct · Q4 (GGUF)",
            runtimeId = "llamacpp",
            url = "https://huggingface.co/unsloth/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it.Q4_K_M.gguf",
            approxSizeBytes = 1_700_000_000,
            fileName = "gemma-2-2b-it.Q4_K_M.gguf",
            notes = "Stronger; wants ~3 GB free RAM.",
        ),
        LocalModel(
            id = "phi3_5-mini-onnx",
            name = "Phi-3.5 Mini Instruct (ONNX GenAI)",
            runtimeId = "onnx",
            url = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/model.onnx.data",
            approxSizeBytes = 2_200_000_000,
            fileName = "phi3.5-mini.onnx.data",
            notes = "ONNX Runtime GenAI; CPU int4.",
        ),
        LocalModel(
            id = "gemma2-2b-it-mediapipe",
            name = "Gemma 2 2B Instruct (MediaPipe .task)",
            runtimeId = "mediapipe",
            url = "https://huggingface.co/google/gemma-2-2b-it/resolve/main/gemma-2-2b-it.task",
            approxSizeBytes = 1_300_000_000,
            fileName = "gemma-2-2b-it.task",
            requiresAuth = true,
            notes = "Gated by Google — requires a Hugging Face token with access granted.",
        ),
    )

    fun byId(id: String): LocalModel? = models.firstOrNull { it.id == id }
    fun forRuntime(runtimeId: String): List<LocalModel> = models.filter { it.runtimeId == runtimeId }
}
