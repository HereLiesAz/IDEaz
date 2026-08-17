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
    /**
     * The model's redistribution license (e.g. "Apache-2.0", "MIT", "Gemma"),
     * verified against the source repository's own declared license as of the
     * last catalog audit. Shown in Settings so a user knows what they're
     * agreeing to before downloading — required for Gemma-licensed entries in
     * particular, since Google's Gemma Terms of Use conditions redistribution
     * on the license being surfaced, not just linked from a README upstream.
     */
    val license: String = "",
    /** Link to the license's full text, shown alongside [license] when non-blank. */
    val licenseUrl: String = "",
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
            license = "Google AICore terms (system service, not a redistributed file)",
            licenseUrl = "https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference",
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
            license = "Apache-2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF",
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
            license = "Gemma",
            licenseUrl = "https://ai.google.dev/gemma/terms",
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
            license = "Gemma",
            licenseUrl = "https://ai.google.dev/gemma/terms",
        ),
        LocalModel(
            // unsloth/gemma-2-2b-it-GGUF (the previous source) is gone - the Hub
            // API 401s on the whole repo, and a broad Hub search for any current
            // "gemma-2-2b-it" GGUF turns up nothing; the quantizer ecosystem has
            // moved on to Gemma 4. unsloth/gemma-4-E2B-it-GGUF is the actively
            // maintained (461k+ downloads), current-generation equivalent -
            // Apache-2.0 licensed (not gated, unlike the old entry), with
            // size/sha256 verified directly against the Hub API. Renamed the id/
            // display name to match what's actually shipped instead of keeping
            // stale "Gemma 2" naming pointed at Gemma 4 weights.
            id = "gemma-4-e2b-it-q4-gguf",
            name = "Gemma 4 (E2B) Instruct · Q4 (GGUF)",
            runtimeId = "llamacpp",
            url = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf",
            approxSizeBytes = 3_106_738_272,
            fileName = "gemma-4-e2b-it-Q4_K_M.gguf",
            expectedSizeBytes = 3_106_738_272,
            sha256 = "740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8",
            minRamBytes = 4_000_000_000,
            requiredAbi = "arm64-v8a",
            notes = "Current-generation replacement for the retired Gemma 2 2B GGUF entry; wants ~4 GB free RAM.",
            license = "Apache-2.0",
            licenseUrl = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF",
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
                license = "MIT",
                licenseUrl = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct-onnx",
            )
        },
        LocalModel(
            id = "gemma2-2b-it-mediapipe",
            name = "Gemma 2 2B Instruct (MediaPipe .task)",
            runtimeId = "mediapipe",
            // litert-community is Google's own org for LiteRT/MediaPipe-format
            // conversions (the previous URL pointed at google/gemma-2-2b-it,
            // which only ships raw .safetensors weights - never had a .task file
            // at all). Confirmed via the Hub tree API: this repo genuinely
            // contains a MediaPipe .task bundle, gated the same way the old
            // entry already expected (license:gemma, requiresAuth = true, same
            // HF-token flow this app already implements). The repo's LFS object
            // hashes are masked while gated/unauthenticated, so sha256 can't be
            // verified without an HF token accepted into this specific repo -
            // expectedSizeBytes is real (visible even gated); sha256 stays null
            // until someone with access can confirm it.
            url = "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/" +
                "Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
            approxSizeBytes = 2_713_274_466,
            fileName = "gemma2-2b-it_multi-prefill-seq_q8_ekv1280.task",
            expectedSizeBytes = 2_713_274_466,
            requiresAuth = true,
            minRamBytes = 6_000_000_000,
            requiredAbi = "arm64-v8a",
            notes = "Gated by Google — requires a Hugging Face token with access granted. " +
                "Q8 quantization; wants ~6 GB free RAM.",
            license = "Gemma",
            licenseUrl = "https://ai.google.dev/gemma/terms",
        ),
    )

    fun byId(id: String): LocalModel? = models.firstOrNull { it.id == id }
    fun forRuntime(runtimeId: String): List<LocalModel> = models.filter { it.runtimeId == runtimeId }
}
