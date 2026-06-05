package android.llama.cpp

/**
 * Minimal JNI binding to llama.cpp. Singleton because the native runtime is
 * process-global. `LlamaCppRuntime` in the app detects this class by name, so its
 * mere presence on the classpath activates the llama.cpp on-device runtime.
 *
 * Synchronous, blocking API (call off the main thread). A streaming variant can
 * be added later (the official llama.android example returns a Flow).
 */
class LLamaAndroid private constructor() {

    @Volatile private var modelPtr: Long = 0L

    private external fun nativeLoad(modelPath: String): Long
    private external fun nativeComplete(modelPtr: Long, prompt: String, maxTokens: Int): String
    private external fun nativeUnload(modelPtr: Long)

    /** Load a GGUF model from [path]. Replaces any previously loaded model. */
    @Synchronized
    fun load(path: String) {
        if (modelPtr != 0L) nativeUnload(modelPtr)
        modelPtr = nativeLoad(path)
        require(modelPtr != 0L) { "Failed to load model: $path" }
    }

    /** Generate a completion for [prompt]. Blocks; run on a background thread. */
    @Synchronized
    fun complete(prompt: String, maxTokens: Int = 512): String {
        check(modelPtr != 0L) { "No model loaded" }
        return nativeComplete(modelPtr, prompt, maxTokens)
    }

    /** Free native resources. */
    @Synchronized
    fun unload() {
        if (modelPtr != 0L) {
            nativeUnload(modelPtr)
            modelPtr = 0L
        }
    }

    companion object {
        private val INSTANCE = LLamaAndroid()
        fun instance(): LLamaAndroid = INSTANCE

        init {
            System.loadLibrary("llama-android")
        }
    }
}
