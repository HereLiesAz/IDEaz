package com.hereliesaz.ideaz.azphalt

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * Process-wide Azphalt store state. Installed LLMs and publisher pins live in the same default
 * SharedPreferences [com.hereliesaz.ideaz.ui.SettingsViewModel] uses. [init] runs from
 * MainApplication; before it (e.g. in JVM unit tests) [llmsOrNull] is null and no store models exist.
 */
object AzphaltStore {
    @Volatile
    private var state: State? = null

    private class State(val kv: AzphaltKeyValueStore) {
        val llms = InstalledLlmStore(kv)
        val pins = KeyValuePublisherPinStore(kv)
    }

    fun init(context: Context) {
        if (state != null) return
        synchronized(this) {
            if (state == null) {
                state = State(SharedPreferencesKeyValueStore(PreferenceManager.getDefaultSharedPreferences(context.applicationContext)))
            }
        }
    }

    val llmsOrNull: InstalledLlmStore? get() = state?.llms

    fun llms(context: Context): InstalledLlmStore {
        init(context)
        return requireNotNull(state).llms
    }

    fun service(context: Context, repositoryUrl: String = AZPHALT_STORE_URL): AzphaltLlmStoreService {
        init(context)
        val s = requireNotNull(state)
        return AzphaltLlmStoreService(AzphaltRepositoryClient(repositoryUrl), AzphaltPackageVerifier(s.pins), s.llms)
    }
}

private class SharedPreferencesKeyValueStore(private val prefs: SharedPreferences) : AzphaltKeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    // commit, not apply: an install must be on disk before the UI reports it.
    override fun putString(key: String, value: String) = prefs.edit(commit = true) { putString(key, value) }
}
