# On-device model support matrix

Closes the "Publish the supported-device/model matrix and experimental
limitations" item in `docs/TODO.md`'s on-device model production checklist.

**What this is:** requirements, sourced directly from the catalog code
(`LocalModelCatalog.kt`) that the app itself enforces before offering a
model for download (`LocalModelAvailability.evaluate`).

**What this is not:** a performance benchmark. Cold-start time,
tokens/second, peak memory, thermal throttling behavior, and repeated-
inference stability have **not** been measured on physical hardware — this
development environment has no Android device or hardware-accurate
emulator available, and this checklist item's own physical-device-testing
sibling (see `docs/TODO.md`) is separately still open for exactly that
reason. Presenting invented numbers here would be worse than presenting
none; the honest state is "not yet measured," not "measured and fast."

## Requirements by model

All entries require the `arm64-v8a` CPU ABI (every on-device runtime is
native/JIT-compiled for ARM64; the app hides a model entirely on a device
that doesn't report this ABI, rather than letting it fail at load time).

| Model | Runtime | Min RAM | Approx. download | License | Gated? |
| --- | --- | --- | --- | --- | --- |
| Gemini Nano (AICore) | AICore (system) | Device-dependent; hardware-gated to Pixel 8+/select Samsung | None (system-managed) | Google AICore terms | No |
| Qwen2.5 0.5B Instruct Q4 | llama.cpp (GGUF) | 2 GB | ~0.49 GB | Apache-2.0 | No |
| Gemma 3 Nano E2B Q4 | llama.cpp (GGUF) | 4 GB | ~2.79 GB | Gemma | No |
| Gemma 3 Nano E4B Q4 | llama.cpp (GGUF) | 6 GB | ~4.54 GB | Gemma | No |
| Gemma 4 E2B Q4 | llama.cpp (GGUF) | 4 GB | ~3.11 GB | Apache-2.0 | No |
| Phi-3.5 Mini Instruct (CPU int4) | ONNX Runtime GenAI | 4 GB | ~2.78 GB (multi-file) | MIT | No |
| Gemma 2 2B Instruct Q8 | MediaPipe LLM Inference | 6 GB | ~2.71 GB | Gemma | Yes — Hugging Face token required |

(Figures above are computed directly from `LocalModelCatalog.kt`'s
`approxSizeBytes`/`expectedSizeBytes` fields, not estimated by eye.)

"Min RAM" is the catalog's own conservative floor for the model to load and
generate at all (`LocalModel.minRamBytes`), not a guarantee of good
performance at that floor — a device right at the minimum should expect
tight headroom for the rest of the app and OS.

## AICore / Gemini Nano hardware gating

AICore availability is probed directly via `GeminiNanoAdapter.isAvailable()`
against the real on-device inference engine, not a static device-model
allowlist — the "Pixel 8+/select Samsung" guidance in the catalog notes is
Google's own general rollout guidance, not something this app hardcodes or
enforces itself.

## llama.cpp / ONNX Runtime GenAI backend availability

Both backends require a native library that isn't bundled in every build
(`docs/on-device-runtimes.md` covers wiring them in). `isAvailable()` checks
for the backend class on the classpath at runtime; a model whose backend
isn't present in the current build is hidden from the picker rather than
shown and failing.

## Prompt/context/output token budgets by RAM tier

From `localInferenceLimits()` in `ai/local/LocalModelRuntime.kt` — shared
across every backend, this bounds how much conversation history and tool
output a local model can see per turn, and how long its replies can run:

| Device RAM | Max context (total) | Max output | Approx. max prompt |
| --- | --- | --- | --- |
| < 4 GB | 2,048 tokens | 256 tokens | ~7,168 chars |
| 4–8 GB | 4,096 tokens | 512 tokens | ~14,336 chars |
| ≥ 8 GB | 6,144 tokens | 768 tokens | ~21,504 chars |

These are conservative software defaults, not benchmarked-optimal values —
see the physical-device-testing caveat above.

## Known catalog limitations

Two prior catalog entries had upstream URLs that stopped resolving and were
corrected this session (see `docs/TODO.md`'s on-device model audit bullets
and `docs/privacy-license-telemetry-review.md` for the evidence). If a
download fails with a clear HTTP error, the source has likely moved again —
please file an issue rather than assuming it's a bug in the download path
itself, which has its own integrity verification (exact size + SHA-256 where
populated) and resume/retry logic independent of the URL being correct.
