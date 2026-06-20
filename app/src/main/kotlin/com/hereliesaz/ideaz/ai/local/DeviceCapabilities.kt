package com.hereliesaz.ideaz.ai.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Reads the device's hardware capabilities relevant to running on-device models:
 * total RAM and supported CPU ABIs. Kept tiny and Android-facing so the decision
 * logic in [LocalModelAvailability] can stay pure and unit-tested.
 */
object DeviceCapabilities {

    /** Total physical RAM in bytes, or 0 if it can't be determined. */
    fun totalRamBytes(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        return try {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mi.totalMem
        } catch (e: Exception) {
            0L
        }
    }

    /** CPU ABIs this device supports (e.g. "arm64-v8a"). Empty if unknown. */
    fun supportedAbis(): Set<String> =
        Build.SUPPORTED_ABIS?.toSet() ?: emptySet()
}
