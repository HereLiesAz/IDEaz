package com.hereliesaz.ideaz.ai.local

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/** Persists which catalog model the user has selected as their on-device model. */
class LocalModelStore(context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    var activeModelId: String?
        get() = prefs.getString(KEY_ACTIVE_MODEL, null)
        set(value) { prefs.edit { putString(KEY_ACTIVE_MODEL, value) } }

    /** Optional token for gated downloads (e.g. a Hugging Face token for Gemma). */
    var downloadToken: String?
        get() = prefs.getString(KEY_DOWNLOAD_TOKEN, null)
        set(value) { prefs.edit { putString(KEY_DOWNLOAD_TOKEN, value) } }

    fun activeModel(): LocalModel? = activeModelId?.let { LocalModelCatalog.byId(it) }

    companion object {
        private const val KEY_ACTIVE_MODEL = "local_model_active_id"
        private const val KEY_DOWNLOAD_TOKEN = "local_model_download_token"
    }
}
