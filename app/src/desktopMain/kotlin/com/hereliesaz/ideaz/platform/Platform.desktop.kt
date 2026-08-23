package com.hereliesaz.ideaz.platform

actual object Platform {
    // Desktop builds are development builds by definition - the target exists so
    // the loop can be exercised by hand - so verbose HTTP logging is on. Requests
    // still pass through LogSanitizer before anything is written.
    actual val isDebugBuild: Boolean get() = true

    actual fun logDebug(tag: String, message: String) { println("D/$tag: $message") }

    actual fun logWarn(tag: String, message: String, error: Throwable?) {
        System.err.println("W/$tag: $message")
        error?.printStackTrace()
    }

    actual fun base64Encode(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)
}
