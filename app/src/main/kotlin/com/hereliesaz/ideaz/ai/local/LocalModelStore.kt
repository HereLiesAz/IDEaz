package com.hereliesaz.ideaz.ai.local

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/** Persists which catalog model the user has selected as their on-device model. */
class LocalModelStore(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    init {
        // Removed pre-Keystore token slot. It had no production reader; retaining it
        // would preserve plaintext forever for no reason except archaeology.
        prefs.edit { remove(LEGACY_DOWNLOAD_TOKEN) }
    }

    var activeModelId: String?
        get() = prefs.getString(KEY_ACTIVE_MODEL, null)
        set(value) {
            if (value != activeModelId) LocalModelRuntimes.requestReleaseAll()
            prefs.edit { putString(KEY_ACTIVE_MODEL, value) }
        }

    fun activeModel(): LocalModel? = activeModelId?.let { LocalModelCatalog.byId(it) }

    companion object {
        private const val KEY_ACTIVE_MODEL = "local_model_active_id"
        private const val LEGACY_DOWNLOAD_TOKEN = "local_model_download_token"
    }
}
