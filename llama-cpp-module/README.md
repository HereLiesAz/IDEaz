# llama-cpp-module (scaffold)

A starting point for running GGUF models on-device via llama.cpp. It is **not**
wired into the IDEaz build (not in `settings.gradle.kts`), so the app builds
without it. Activating it makes `LlamaCppRuntime` (`ai/local/LocalModelRuntime.kt`)
usable, because that runtime already detects `android.llama.cpp.LLamaAndroid`.

llama.cpp has no published Android Maven artifact, so it must be built from source
with the NDK — hence a module rather than a dependency line.

## Activate

1. Add llama.cpp as a submodule inside this module:
   ```
   git submodule add https://github.com/ggml-org/llama.cpp llama-cpp-module/llama.cpp
   ```
   Pin a known-good tag; the C API in `llama-android.cpp` targets a recent
   release and may need small adjustments to match the tag you pin.

2. Include + depend on the module:
   - `settings.gradle.kts`: `include(":llama-cpp-module")`
   - `app/build.gradle.kts`: `implementation(project(":llama-cpp-module"))`

3. Implement `LlamaCppRuntime.generate()` (see `docs/on-device-runtimes.md`) to
   call `android.llama.cpp.LLamaAndroid`.

4. R8 (`app/proguard-rules.pro`): `-keep class android.llama.cpp.** { *; }`

## Notes
- `abiFilters` is set to `arm64-v8a` only (most phones). Add `x86_64` for the
  emulator. Each ABI multiplies native size + build time.
- This adds tens of MB of native code; verify APK size and on-device inference
  on real hardware. None of this can be compile- or run-verified without the
  submodule + NDK toolchain.
