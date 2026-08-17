package com.hereliesaz.ideaz.ai.local

/**
 * A downloadable on-device model.
 *
 * [requiresAuth] models (e.g. gated Gemma) need a provider token the user supplies
 * before the download will succeed. URLs are direct download links; the download
 * manager is URL-generic, so this catalog can grow without code changes.
 */
/** One file belonging to a downloadable model, including its trusted manifest data. */
data class LocalModelFile(
    val url: String,
    val fileName: String,
    val expectedSizeBytes: Long? = null,
    val sha256: String? = null,
)

data class LocalModel(
    val id: String,
    val name: String,
    val runtimeId: String,
    val url: String,
    val approxSizeBytes: Long,
    val fileName: String,
    /** Trusted exact size for the primary file; null until its catalog entry is audited. */
    val expectedSizeBytes: Long? = null,
    /** Trusted lowercase/uppercase SHA-256 for the primary file. */
    val sha256: String? = null,
    val requiresAuth: Boolean = false,
    /** True for runtimes that manage their own model (e.g. AICore): no file download. */
    val systemManaged: Boolean = false,
    /**
     * Extra files for multi-file models. ONNX GenAI ships a *directory* (model +
     * genai_config.json + tokenizer files); all files download into the model's
     * own directory, which the runtime is pointed at. Empty for single-file
     * models (MediaPipe `.task`, llama.cpp `.gguf`).
     */
    val additionalFiles: List<LocalModelFile> = emptyList(),
    /**
     * Minimum total device RAM (bytes) the model realistically needs to load and
     * run. 0 = no requirement (e.g. system-managed AICore). Used to hide models a
     * device can't actually run.
     */
    val minRamBytes: Long = 0,
    /**
     * CPU ABI the model's runtime needs (e.g. "arm64-v8a" for the native GGUF /
     * ONNX backends). null = no requirement. Used to hide models whose native code
     * can't run on this device's architecture.
     */
    val requiredAbi: String? = null,
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
            approxSizeBytes = 491_400_032,
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            // Verified against the Hugging Face Hub tree API (LFS oid/size), not by
            // downloading the file - see docs/TODO.md for how this was obtained.
            expectedSizeBytes = 491_400_032,
            sha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            minRamBytes = 2_000_000_000,
            requiredAbi = "arm64-v8a",
            notes = "Tiny and fast; good for low-RAM devices.",
        ),
        LocalModel(
            id = "gemma-3n-e2b-it-q4-gguf",
            name = "Gemma 3 Nano (E2B) Instruct · Q4 (GGUF)",
            runtimeId = "llamacpp",
            url = "https://huggingface.co/lmstudio-community/gemma-3n-E2B-it-text-GGUF/resolve/main/gemma-3n-E2B-it-Q4_K_M.gguf",
            approxSizeBytes = 2_787_805_568,
            fileName = "gemma-3n-e2b-it-Q4_K_M.gguf",
            expectedSizeBytes = 2_787_805_568,
            sha256 = "4eff1cf815bdcb15c63575d266064ad609563c835a39b324531ad9266f2dd862",
            minRamBytes = 4_000_000_000,
            requiredAbi = "arm64-v8a",
            notes = "Gemma 3 Nano E2B (2B effective parameters). Good balance of speed and quality.",
        ),
        LocalModel(
            id = "gemma-3n-e4b-it-q4-gguf",
            name = "Gemma 3 Nano (E4B) Instruct · Q4 (GGUF)",
            runtimeId = "llamacpp",
            url = "https://huggingface.co/unsloth/gemma-3n-E4B-it-GGUF/resolve/main/gemma-3n-E4B-it-Q4_K_M.gguf",
            approxSizeBytes = 4_539_054_208,
            fileName = "gemma-3n-e4b-it-Q4_K_M.gguf",
            expectedSizeBytes = 4_539_054_208,
            sha256 = "43b489bb77a81bda85180e7c490d40ad7f1d5c2ce654c9b05e15e104bd3c777e",
            minRamBytes = 6_000_000_000,
            requiredAbi = "arm64-v8a",
            notes = "Gemma 3 Nano E4B (4B effective parameters). Needs ~5 GB RAM.",
        ),
        LocalModel(
            id = "gemma2-2b-it-q4-gguf",
            name = "Gemma 2 2B Instruct · Q4 (GGUF)",
            runtimeId = "llamacpp",
            url = "https://huggingface.co/unsloth/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it.Q4_K_M.gguf",
            approxSizeBytes = 1_700_000_000,
            fileName = "gemma-2-2b-it.Q4_K_M.gguf",
            minRamBytes = 4_000_000_000,
            requiredAbi = "arm64-v8a",
            // unsloth/gemma-2-2b-it-GGUF's Hub API returned 401 "Invalid username or
            // password" for this entire repo as of this audit pass (not just this
            // file) - it may have been renamed, made private, or moved. The URL is
            // left as-is (still worth trying at download time) but expectedSizeBytes/
            // sha256 are deliberately NOT populated: this needs a human to locate the
            // model's current home before its integrity can be pinned.
            notes = "Stronger; wants ~3 GB free RAM. NEEDS VERIFICATION: source repo returned 401 as of the last audit pass.",
        ),
        run {
            // The catalog previously pointed at cpu_and_mobile/cpu-int4-rtn-block-32-
            // acc-level-4/, which the Hub API confirms no longer exists in this repo
            // (every download would 404). Repointed to the current
            // cpu-int4-awq-block-128-acc-level-4/ variant and re-verified size/sha256
            // against the Hub API for every file in the directory.
            val base = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct-onnx/resolve/main/" +
                "cpu_and_mobile/cpu-int4-awq-block-128-acc-level-4/"
            val onnxFile = "phi-3.5-mini-instruct-cpu-int4-awq-block-128-acc-level-4.onnx"
            LocalModel(
                id = "phi3_5-mini-onnx",
                name = "Phi-3.5 Mini Instruct (ONNX GenAI · CPU int4)",
                runtimeId = "onnx",
                url = base + onnxFile,
                approxSizeBytes = 52_176_615,
                fileName = onnxFile,
                expectedSizeBytes = 52_176_615,
                sha256 = "c4f05e6ef52f2588df181e566afbf5e8eeba097fece2fc8246770473a10225fd",
                additionalFiles = listOf(
                    LocalModelFile(
                        base + "$onnxFile.data", "$onnxFile.data",
                        expectedSizeBytes = 2_728_144_896,
                        sha256 = "3351fe9cc669eba43e07fb3cec436078629d5145531a28bc36fe6d5ad7683eb8",
                    ),
                    LocalModelFile(base + "genai_config.json", "genai_config.json"),
                    LocalModelFile(base + "tokenizer.json", "tokenizer.json"),
                    LocalModelFile(base + "tokenizer_config.json", "tokenizer_config.json"),
                    LocalModelFile(base + "special_tokens_map.json", "special_tokens_map.json"),
                ),
                minRamBytes = 4_000_000_000,
                requiredAbi = "arm64-v8a",
                notes = "ONNX Runtime GenAI; multi-file model directory.",
            )
        },
        LocalModel(
            id = "gemma2-2b-it-mediapipe",
            name = "Gemma 2 2B Instruct (MediaPipe .task)",
            runtimeId = "mediapipe",
            url = "https://huggingface.co/google/gemma-2-2b-it/resolve/main/gemma-2-2b-it.task",
            approxSizeBytes = 1_300_000_000,
            fileName = "gemma-2-2b-it.task",
            requiresAuth = true,
            minRamBytes = 4_000_000_000,
            requiredAbi = "arm64-v8a",
            // google/gemma-2-2b-it's Hub tree API (checked during this audit pass) does
            // NOT contain a "gemma-2-2b-it.task" file at all - that repo ships raw
            // .safetensors weights, not a MediaPipe-converted .task bundle. This URL
            // 404s. A correct MediaPipe .task source (e.g. a litert-community
            // conversion) needs to be located and its license/redistribution terms
            // checked before this entry can be pointed at it.
            notes = "Gated by Google — requires a Hugging Face token with access granted. " +
                "BROKEN: source repo has no .task file; needs a corrected URL.",
        ),
    )

    fun byId(id: String): LocalModel? = models.firstOrNull { it.id == id }
    fun forRuntime(runtimeId: String): List<LocalModel> = models.filter { it.runtimeId == runtimeId }
}
