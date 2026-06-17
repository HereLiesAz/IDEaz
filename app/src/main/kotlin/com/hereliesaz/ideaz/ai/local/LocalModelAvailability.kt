package com.hereliesaz.ideaz.ai.local

/**
 * Pure decision logic for "can this device actually use this model right now?".
 * Framework-free so it can be unit-tested; the Settings UI feeds it real device
 * signals (RAM, ABIs, backend presence, auth) and shows only the [Status.Usable]
 * models, listing the rest with a reason.
 */
object LocalModelAvailability {

    sealed interface Status {
        /** The model can be selected (and downloaded) on this device/build. */
        object Usable : Status

        /** The model can't be used now; [reason] is a short user-facing explanation. */
        data class Unsupported(val reason: String) : Status
    }

    /**
     * @param backendAvailable whether the model's runtime backend is present AND
     *   (for system-managed runtimes like AICore) actually supported by the device.
     * @param backendName display name of the runtime, for the "not in this build" reason.
     * @param totalRamBytes device RAM; 0 means unknown (RAM check skipped).
     * @param abis device-supported ABIs; empty means unknown (ABI check skipped).
     * @param hasAuthToken whether a download/auth token is present (for gated models).
     */
    fun evaluate(
        model: LocalModel,
        backendAvailable: Boolean,
        backendName: String,
        totalRamBytes: Long,
        abis: Set<String>,
        hasAuthToken: Boolean,
    ): Status {
        if (!backendAvailable) {
            return Status.Unsupported(
                if (model.systemManaged) "Not supported on this device"
                else "$backendName backend not in this build",
            )
        }
        val abi = model.requiredAbi
        if (abi != null && abis.isNotEmpty() && abi !in abis) {
            return Status.Unsupported("Needs a $abi CPU")
        }
        if (model.minRamBytes > 0 && totalRamBytes > 0 && totalRamBytes < model.minRamBytes) {
            val gb = (model.minRamBytes + 999_999_999) / 1_000_000_000 // round up
            return Status.Unsupported("Needs ~$gb GB RAM")
        }
        if (model.requiresAuth && !hasAuthToken) {
            return Status.Unsupported("Needs a Hugging Face token")
        }
        return Status.Usable
    }
}
