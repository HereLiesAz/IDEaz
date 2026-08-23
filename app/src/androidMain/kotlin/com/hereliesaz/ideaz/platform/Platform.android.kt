package com.hereliesaz.ideaz.platform

import android.util.Base64
import android.util.Log
import com.hereliesaz.ideaz.BuildConfig

actual object Platform {
    actual val isDebugBuild: Boolean get() = BuildConfig.DEBUG

    actual fun logDebug(tag: String, message: String) { Log.d(tag, message) }

    actual fun logWarn(tag: String, message: String, error: Throwable?) {
        if (error != null) Log.w(tag, message, error) else Log.w(tag, message)
    }

    actual fun base64Encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
