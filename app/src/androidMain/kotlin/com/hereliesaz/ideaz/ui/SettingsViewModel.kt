package com.hereliesaz.ideaz.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.BuildConfig
import com.hereliesaz.ideaz.ai.aive.AivePairingCode
import com.hereliesaz.ideaz.utils.AndroidKeystoreCredentialStore
import com.hereliesaz.ideaz.utils.CredentialStore
import com.hereliesaz.ideaz.utils.SecurityUtils
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

// Define AI models and their requirements.
//
// providerKey is the credential slot used by a provider API. requiredKey is the
// setup-time gate: in GitHub builds Gemini can be supplied by the installed-app
// bridge and therefore does not require an API key, while Play remains API-only.
//
// supportsImages: whether the provider accepts image/reference parts in prompts.
// keyless: the provider answers anonymous requests (the same hosted endpoints The Aive
// ships keyless), so it has no credential slot (providerKey is empty).
data class AiModel(
    val id: String,
    val displayName: String,
    val providerKey: String,
    /** Wire model id sent to the provider. Overridable per-provider in Settings. */
    val defaultWireModel: String,
    val supportsImages: Boolean = false,
    val keyless: Boolean = false,
) {
    val requiredKey: String
        get() = if (keyless || (id == "GEMINI_FLASH" && BuildConfig.EXTERNAL_AI_AUTOMATION)) "" else providerKey
}

/** Registered conversational AI providers. */
object AiModels {
    const val GEMINI_FLASH = "GEMINI_FLASH"
    const val GROQ_LLAMA = "GROQ_LLAMA"
    const val CEREBRAS_LLAMA = "CEREBRAS_LLAMA"
    const val HF_INFERENCE = "HF_INFERENCE"
    const val MISTRAL_SMALL = "MISTRAL_SMALL"
    const val OPENAI_GPT4O = "OPENAI_GPT4O"
    const val ANTHROPIC_CLAUDE = "ANTHROPIC_CLAUDE"
    const val DEEPSEEK_CODER = "DEEPSEEK_CODER"
    const val KILO_FREE = "KILO_FREE"
    const val LLM7_DEFAULT = "LLM7_DEFAULT"
    const val OVHCLOUD_FREE = "OVHCLOUD_FREE"
    const val THE_AIVE = "THE_AIVE"

    val GEMINI = AiModel(
        GEMINI_FLASH, "Gemini 2.0 Flash", SettingsViewModel.KEY_GOOGLE_API_KEY,
        defaultWireModel = "gemini-2.0-flash", supportsImages = true,
    )
    val ANTHROPIC = AiModel(
        ANTHROPIC_CLAUDE, "Anthropic · Claude Sonnet", SettingsViewModel.KEY_ANTHROPIC_API_KEY,
        defaultWireModel = "claude-sonnet-4-20250514", supportsImages = true,
    )
    val OPENAI = AiModel(
        OPENAI_GPT4O, "OpenAI · GPT-4o", SettingsViewModel.KEY_OPENAI_API_KEY,
        defaultWireModel = "gpt-4o", supportsImages = true,
    )
    val DEEPSEEK = AiModel(
        DEEPSEEK_CODER, "DeepSeek · Chat", SettingsViewModel.KEY_DEEPSEEK_API_KEY,
        defaultWireModel = "deepseek-chat",
    )
    val GROQ = AiModel(
        GROQ_LLAMA, "Groq · Llama 3.3 70B", SettingsViewModel.KEY_GROQ_API_KEY,
        defaultWireModel = "llama-3.3-70b-versatile",
    )
    val CEREBRAS = AiModel(
        CEREBRAS_LLAMA, "Cerebras · Llama 3.3 70B", SettingsViewModel.KEY_CEREBRAS_API_KEY,
        defaultWireModel = "llama-3.3-70b",
    )
    val HF = AiModel(
        HF_INFERENCE, "Hugging Face · Llama 3.3 70B", SettingsViewModel.KEY_HF_API_KEY,
        defaultWireModel = "meta-llama/Llama-3.3-70B-Instruct",
    )
    val MISTRAL = AiModel(
        MISTRAL_SMALL, "Mistral Small", SettingsViewModel.KEY_MISTRAL_API_KEY,
        defaultWireModel = "mistral-small-latest",
    )

    // Keyless: anonymous, heavily rate-limited, so ranked last. Endpoints and models mirror
    // The Aive's HostedLlmProviders keyless entries.
    val KILO = AiModel(
        KILO_FREE, "Kilo Gateway · Free (keyless)", providerKey = "",
        defaultWireModel = "kilo-auto/free", keyless = true,
    )
    val LLM7 = AiModel(
        LLM7_DEFAULT, "LLM7 · Default (keyless)", providerKey = "",
        // LLM7's alias for whatever it currently serves; named models come and go.
        defaultWireModel = "default", keyless = true,
    )
    val OVHCLOUD = AiModel(
        OVHCLOUD_FREE, "OVHcloud · gpt-oss-120b (keyless)", providerKey = "",
        defaultWireModel = "gpt-oss-120b", keyless = true,
    )

    // Runs on whatever provider is linked in The Aive: installed here (same signing key), or on
    // another device through the relay once a pairing code is saved.
    // Keys live in The Aive, so there is no slot here; the wire model is The Aive's choice.
    val AIVE = AiModel(
        THE_AIVE, "The Aive (linked provider)", providerKey = "",
        defaultWireModel = "",
    )

    val availableModels = listOf(GEMINI, ANTHROPIC, OPENAI, DEEPSEEK, GROQ, CEREBRAS, HF, MISTRAL, KILO, LLM7, OVHCLOUD, AIVE)
    /** Keyed providers by preference, then the keyless ones as the last fallback. */
    val defaultRanking = listOf(
        GEMINI, ANTHROPIC, OPENAI, DEEPSEEK, GROQ, CEREBRAS, HF, MISTRAL,
        KILO, LLM7, OVHCLOUD,
    )

    /**
     * The model id used when the user has not chosen one: the highest-ranked provider with a
     * saved key; else Gemini when the installed-Gemini bridge exists (GitHub builds); else the
     * first keyless model, so a fresh Play install works without any key.
     */
    fun pickDefault(hasKey: (AiModel) -> Boolean, bridgeAvailable: Boolean): String =
        defaultRanking.firstOrNull { !it.keyless && it.providerKey.isNotEmpty() && hasKey(it) }?.id
            ?: if (bridgeAvailable) GEMINI_FLASH else (defaultRanking.firstOrNull { it.keyless }?.id ?: GEMINI_FLASH)

    fun findById(id: String?): AiModel? = availableModels.find { it.id == id }
}

@Serializable
data class SettingsExport(
    val strings: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val longs: Map<String, Long> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    val stringSets: Map<String, Set<String>> = emptyMap(),
    val keystore: KeystoreData? = null
)

@Serializable
data class KeystoreData(
    val filename: String,
    val contentBase64: String
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
    private var credentialStore: CredentialStore = AndroidKeystoreCredentialStore(application)

    internal constructor(application: Application, credentialStore: CredentialStore) : this(application) {
        this.credentialStore = credentialStore
    }

    companion object {
        private const val TAG = "SettingsViewModel"
        const val KEY_API_KEY = "api_key"
        const val KEY_APP_NAME = "app_name"
        const val KEY_REPO_DESCRIPTION = "repo_description"
        const val KEY_GITHUB_USER = "github_user"
        const val KEY_BRANCH_NAME = "branch_name"
        const val KEY_PROJECT_LIST = "project_list"
        const val KEY_PROJECT_PATHS = "project_paths"

        /**
         * Settings that describe this device's own projects. An export from another device must not
         * overwrite them: a foreign absolute path in project_paths would be returned by
         * getProjectPath while project creation uses filesDir.
         */
        /** Provider cooldown expiries ("id=epochMillis;..."); see [com.hereliesaz.ideaz.ai.ProviderCooldowns]. */
        const val KEY_PROVIDER_COOLDOWNS = "provider_cooldowns"
        val DEVICE_LOCAL_KEYS: Set<String> = setOf(KEY_PROJECT_LIST, KEY_PROJECT_PATHS, KEY_APP_NAME, KEY_KEYSTORE_PATH, KEY_PROVIDER_COOLDOWNS)
        const val KEY_GOOGLE_API_KEY = "google_api_key"
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_JULES_PROJECT_ID = "jules_project_id"

        const val KEY_GROQ_API_KEY = "groq_api_key"
        const val KEY_CEREBRAS_API_KEY = "cerebras_api_key"
        const val KEY_HF_API_KEY = "hf_api_key"
        const val KEY_MISTRAL_API_KEY = "mistral_api_key"

        const val KEY_OPENAI_API_KEY = "openai_api_key"
        const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"

        /** The Aive pairing code for a remote device. A bearer secret (room token + key), so it is kept like an API key. */
        const val KEY_AIVE_PAIRING_CODE = "aive_pairing_code"

        private val SECURE_CREDENTIAL_KEYS = setOf(
            KEY_GOOGLE_API_KEY,
            KEY_HF_API_KEY,
            KEY_API_KEY,
            KEY_GITHUB_TOKEN,
            KEY_GROQ_API_KEY,
            KEY_CEREBRAS_API_KEY,
            KEY_MISTRAL_API_KEY,
            KEY_OPENAI_API_KEY,
            KEY_ANTHROPIC_API_KEY,
            KEY_DEEPSEEK_API_KEY,
            KEY_AIVE_PAIRING_CODE,
            KEY_KEYSTORE_PASS,
            KEY_KEY_PASS,
        )

        const val KEY_BRIDGE_FIRST_RUN_SHOWN = "bridge_first_run_shown"

        const val KEY_REPO_CAN_PUSH = "repo_can_push"
        const val KEY_REPO_IS_ADMIN = "repo_is_admin"
        const val KEY_BRANCH_PROTECTED = "branch_protected"
        const val KEY_PR_REQUIRED = "pr_required"

        const val KEY_AI_ASSIGNMENT_DEFAULT = "ai_assignment_default"
        const val KEY_AI_ASSIGNMENT_CONTEXTLESS = "ai_assignment_contextless"
        const val KEY_AI_ASSIGNMENT_OVERLAY = "ai_assignment_overlay"

        const val KEY_SHOW_CANCEL_WARNING = "show_cancel_warning"
        const val KEY_DEPLOY_CONFIRMED = "deploy_confirmed"

        const val KEY_THEME_MODE = "theme_mode"
        const val THEME_AUTO = "auto"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"

        const val KEY_LOG_LEVEL = "log_level"
        const val LOG_LEVEL_INFO = "info"
        const val LOG_LEVEL_DEBUG = "debug"
        const val LOG_LEVEL_VERBOSE = "verbose"

        const val KEY_KEYSTORE_PATH = "keystore_path"
        /** The only name a keystore is stored under; excluded in backup_rules / data_extraction_rules. */
        const val KEYSTORE_FILENAME = "user_release.keystore"
        const val KEY_KEYSTORE_PASS = "keystore_pass"
        const val KEY_KEY_ALIAS = "key_alias"
        const val KEY_KEY_PASS = "key_pass"
        const val KEY_LAST_PROMPT = "last_prompt"

        val aiTasks = mapOf(
            KEY_AI_ASSIGNMENT_DEFAULT to "Default",
            KEY_AI_ASSIGNMENT_CONTEXTLESS to "Prompt Popup",
            KEY_AI_ASSIGNMENT_OVERLAY to "Overlay Chat"
        )
    }

    private val _localProjects = MutableStateFlow<List<String>>(emptyList())
    val localProjects = _localProjects.asStateFlow()

    private val _currentAppName = MutableStateFlow(getAppName())
    val currentAppName = _currentAppName.asStateFlow()

    private val _apiKey = MutableStateFlow(getApiKey())
    val apiKey = _apiKey.asStateFlow()

    var lastCredentialError: String? = null
        private set

    private val _settingsVersion = MutableStateFlow(0)
    val settingsVersion = _settingsVersion.asStateFlow()

    private val _logLevel = MutableStateFlow(LOG_LEVEL_INFO)
    val logLevel = _logLevel.asStateFlow()

    private val _themeMode = MutableStateFlow(THEME_AUTO)
    val themeMode = _themeMode.asStateFlow()

    private val preferenceChangeListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_THEME_MODE -> _themeMode.value = getThemeMode()
                KEY_LOG_LEVEL -> _logLevel.value = getLogLevel()
            }
        }

    init {
        removeLegacyReportingState()
        loadLocalProjects()
        _themeMode.value = getThemeMode()
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    /**
     * IDEaz no longer reports errors, crashes or build logs anywhere. Earlier versions kept a
     * consent flag, an on/off toggle and a duplicate-report ledger for that; drop what they left
     * behind so a settings export no longer carries it.
     */
    private fun removeLegacyReportingState() {
        runCatching {
            sharedPreferences.edit {
                remove("report_ide_errors")
                remove("crash_reporting_first_run_shown")
                remove("auto_debug_builds")
            }
            getApplication<Application>().deleteSharedPreferences("bug_report_prefs")
        }
    }

    fun isDeployConfirmed() = sharedPreferences.getBoolean(KEY_DEPLOY_CONFIRMED, false)
    fun setDeployConfirmed(confirmed: Boolean) = sharedPreferences.edit { putBoolean(KEY_DEPLOY_CONFIRMED, confirmed) }

    fun getShowCancelWarning() = sharedPreferences.getBoolean(KEY_SHOW_CANCEL_WARNING, true)
    fun setShowCancelWarning(show: Boolean) = sharedPreferences.edit { putBoolean(KEY_SHOW_CANCEL_WARNING, show) }

    fun getThemeMode() = sharedPreferences.getString(KEY_THEME_MODE, THEME_AUTO) ?: THEME_AUTO
    fun setThemeMode(mode: String) {
        sharedPreferences.edit { putString(KEY_THEME_MODE, mode) }
        _themeMode.value = mode
    }

    fun getLogLevel() = sharedPreferences.getString(KEY_LOG_LEVEL, LOG_LEVEL_INFO) ?: LOG_LEVEL_INFO
    fun setLogLevel(level: String) {
        sharedPreferences.edit { putString(KEY_LOG_LEVEL, level) }
        _logLevel.value = level
    }

    fun saveGoogleApiKey(apiKey: String): Boolean = saveString(KEY_GOOGLE_API_KEY, apiKey)
    fun getGoogleApiKey(): String? = getApiKey(KEY_GOOGLE_API_KEY)

    fun saveGithubToken(token: String): Boolean = saveString(KEY_GITHUB_TOKEN, token)
    fun getGithubToken() = getApiKey(KEY_GITHUB_TOKEN)

    fun saveJulesProjectId(projectId: String) = sharedPreferences.edit { putString(KEY_JULES_PROJECT_ID, projectId.trim()) }
    fun getJulesProjectId() = sharedPreferences.getString(KEY_JULES_PROJECT_ID, null)

    fun saveApiKey(apiKey: String): Boolean {
        val trimmed = apiKey.trim()
        val saved = saveString(KEY_API_KEY, trimmed)
        _apiKey.value = trimmed
        return saved
    }

    fun getWireModelOverride(modelId: String): String? =
        sharedPreferences.getString(wireModelKey(modelId), null)?.takeIf { it.isNotBlank() }

    fun setWireModelOverride(modelId: String, wireModel: String?) {
        sharedPreferences.edit().apply {
            if (wireModel.isNullOrBlank()) remove(wireModelKey(modelId))
            else putString(wireModelKey(modelId), wireModel.trim())
        }.apply()
    }

    private fun wireModelKey(modelId: String) = "wire_model_$modelId"

    /** Persists which providers are cooling down after a quota error, so a restart does not forget. */
    fun providerCooldownStore(): com.hereliesaz.ideaz.ai.CooldownStore = object : com.hereliesaz.ideaz.ai.CooldownStore {
        override fun read(): String? = sharedPreferences.getString(KEY_PROVIDER_COOLDOWNS, null)
        override fun write(value: String) {
            sharedPreferences.edit { putString(KEY_PROVIDER_COOLDOWNS, value) }
        }
    }

    fun getAivePairingCode(): String? = getApiKey(KEY_AIVE_PAIRING_CODE)?.takeIf { it.isNotBlank() }

    /** Validates and stores [code] (blank removes it). Returns null on success, else a message for the user. */
    fun saveAivePairingCode(code: String): String? {
        val trimmed = code.trim()
        if (trimmed.isNotEmpty()) {
            try {
                AivePairingCode.parse(trimmed)
            } catch (e: IllegalArgumentException) {
                return e.message ?: "Invalid pairing code."
            }
        }
        return if (saveString(KEY_AIVE_PAIRING_CODE, trimmed)) null
        else lastCredentialError?.let { "Could not store the pairing code: $it" } ?: "Could not store the pairing code."
    }

    fun getApiKey() = getApiKey(KEY_API_KEY)
    fun getApiKey(keyName: String): String? {
        if (keyName !in SECURE_CREDENTIAL_KEYS) return sharedPreferences.getString(keyName, null)
        runCatching { credentialStore.get(keyName) }
            .onFailure {
                Log.e(TAG, "Failed to read secure credential: $keyName", it)
                lastCredentialError = it.message ?: it::class.simpleName
            }
            .getOrNull()
            ?.let { secureValue ->
                if (sharedPreferences.contains(keyName) &&
                    !sharedPreferences.edit().remove(keyName).commit()
                ) {
                    Log.e(TAG, "Failed to remove legacy secure credential: $keyName")
                }
                return secureValue
            }
        val legacy = sharedPreferences.getString(keyName, null) ?: return null
        return runCatching { credentialStore.put(keyName, legacy) }
            .onFailure {
                Log.e(TAG, "Failed to migrate secure credential: $keyName", it)
                lastCredentialError = it.message ?: it::class.simpleName
            }
            .onSuccess {
                if (!sharedPreferences.edit().remove(keyName).commit()) {
                    Log.e(TAG, "Failed to remove migrated secure credential: $keyName")
                }
            }
            .let { legacy }
    }

    fun saveString(keyName: String, value: String): Boolean {
        val trimmed = value.trim()
        if (keyName in SECURE_CREDENTIAL_KEYS) {
            val saved = runCatching {
                if (trimmed.isEmpty()) credentialStore.remove(keyName) else credentialStore.put(keyName, trimmed)
            }.onFailure {
                Log.e(TAG, "Failed to store secure credential: $keyName", it)
                lastCredentialError = it.message ?: it::class.simpleName
            }.isSuccess
            if (saved && !sharedPreferences.edit().remove(keyName).commit()) {
                Log.e(TAG, "Failed to remove legacy plaintext credential: $keyName")
            }
            return saved
        }
        sharedPreferences.edit { putString(keyName, value.trim()) }
        return true
    }

    fun hasShownBridgeFirstRun(): Boolean =
        sharedPreferences.getBoolean(KEY_BRIDGE_FIRST_RUN_SHOWN, false)

    fun markBridgeFirstRunShown() {
        sharedPreferences.edit { putBoolean(KEY_BRIDGE_FIRST_RUN_SHOWN, true) }
    }

    fun saveAiAssignment(taskKey: String, modelId: String) = sharedPreferences.edit { putString(taskKey, modelId) }
    fun getAiAssignment(taskKey: String): String? {
        val defaultModelId = sharedPreferences.getString(KEY_AI_ASSIGNMENT_DEFAULT, null) ?: defaultModelId()
        if (taskKey == KEY_AI_ASSIGNMENT_DEFAULT) return defaultModelId
        return sharedPreferences.getString(taskKey, defaultModelId)
    }

    /** Highest-ranked configured API model; else Gemini on bridge builds, else a keyless model. */
    private fun defaultModelId(): String = AiModels.pickDefault(
        hasKey = { !getApiKey(it.providerKey).isNullOrBlank() },
        bridgeAvailable = com.hereliesaz.ideaz.BuildConfig.EXTERNAL_AI_AUTOMATION,
    )

    /** The default model the Settings dropdowns show for a task that has no explicit choice. */
    fun effectiveDefaultModelId(): String = getAiAssignment(KEY_AI_ASSIGNMENT_DEFAULT) ?: defaultModelId()

    /**
     * Copies the picked keystore to [KEYSTORE_FILENAME] and records its path.
     * Returns null - recording nothing - when it could not be read or copied:
     * this used to save the path even when openInputStream returned null, and
     * a copy failing mid-stream left a truncated file that stayed in use. The
     * copy goes to a temporary file first and replaces the keystore only once
     * complete.
     */
    fun importKeystore(context: Context, uri: Uri): String? {
        val destFile = File(context.filesDir, KEYSTORE_FILENAME)
        val partFile = File(context.filesDir, "$KEYSTORE_FILENAME.part")
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("Could not open $uri")
            input.use { source -> FileOutputStream(partFile).use { source.copyTo(it) } }
            if (partFile.length() == 0L) throw java.io.IOException("The keystore file is empty")
            if (!partFile.renameTo(destFile)) throw java.io.IOException("Could not replace $KEYSTORE_FILENAME")
            val path = destFile.absolutePath
            sharedPreferences.edit { putString(KEY_KEYSTORE_PATH, path) }
            path
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import keystore", e)
            partFile.delete()
            null
        }
    }

    fun saveSigningCredentials(storePass: String, alias: String, keyPass: String): Boolean {
        val storePassSaved = saveString(KEY_KEYSTORE_PASS, storePass)
        sharedPreferences.edit { putString(KEY_KEY_ALIAS, alias) }
        val keyPassSaved = saveString(KEY_KEY_PASS, keyPass)
        return storePassSaved && keyPassSaved
    }

    fun getKeystorePath() = sharedPreferences.getString(KEY_KEYSTORE_PATH, null)
    fun getKeystorePass() = getApiKey(KEY_KEYSTORE_PASS) ?: "android"
    fun getKeyAlias() = sharedPreferences.getString(KEY_KEY_ALIAS, "androiddebugkey") ?: "androiddebugkey"
    fun getKeyPass() = getApiKey(KEY_KEY_PASS) ?: "android"

    fun clearSigningConfig() {
        sharedPreferences.edit { remove(KEY_KEYSTORE_PATH).remove(KEY_KEY_ALIAS) }
        runCatching { credentialStore.remove(KEY_KEYSTORE_PASS) }
        runCatching { credentialStore.remove(KEY_KEY_PASS) }
        runCatching { File(getApplication<Application>().filesDir, KEYSTORE_FILENAME).delete() }
    }

    fun exportSettings(context: Context, uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefsMap = sharedPreferences.all
                val strings = mutableMapOf<String, String>()
                val booleans = mutableMapOf<String, Boolean>()
                val ints = mutableMapOf<String, Int>()
                val longs = mutableMapOf<String, Long>()
                val floats = mutableMapOf<String, Float>()
                val stringSets = mutableMapOf<String, Set<String>>()

                prefsMap.forEach { (key, value) ->
                    if (key in SECURE_CREDENTIAL_KEYS) return@forEach
                    if (value != null) {
                        when (value) {
                            is String -> strings[key] = value
                            is Boolean -> booleans[key] = value
                            is Int -> ints[key] = value
                            is Long -> longs[key] = value
                            is Float -> floats[key] = value
                            is Set<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                stringSets[key] = value as Set<String>
                            }
                        }
                    }
                }
                SECURE_CREDENTIAL_KEYS.forEach { key ->
                    getApiKey(key)?.let { strings[key] = it }
                }
                var keystoreData: KeystoreData? = null
                val keystorePath = getKeystorePath()
                if (keystorePath != null) {
                    val file = File(keystorePath)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        keystoreData = KeystoreData(file.name, base64)
                    }
                }
                val export = SettingsExport(strings, booleans, ints, longs, floats, stringSets, keystoreData)
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val jsonString = json.encodeToString(export)
                val encrypted = SecurityUtils.encrypt(jsonString, password)

                context.contentResolver.openOutputStream(uri)?.use { it.write(encrypted.toByteArray(Charsets.UTF_8)) }
                withContext(Dispatchers.Main) { Toast.makeText(context, "Settings exported successfully", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun importSettings(context: Context, uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encrypted = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw Exception("Failed to read file")
                val jsonString = SecurityUtils.decrypt(encrypted, password)
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val export = json.decodeFromString<SettingsExport>(jsonString)

                SECURE_CREDENTIAL_KEYS.forEach { key ->
                    export.strings[key]?.let { credentialStore.put(key, it) }
                }

                sharedPreferences.edit {
                    export.strings.filterKeys { it !in SECURE_CREDENTIAL_KEYS && it !in DEVICE_LOCAL_KEYS }
                        .forEach { (k, v) -> putString(k, v) }
                    export.booleans.forEach { (k, v) -> putBoolean(k, v) }
                    export.ints.forEach { (k, v) -> putInt(k, v) }
                    export.longs.forEach { (k, v) -> putLong(k, v) }
                    export.floats.forEach { (k, v) -> putFloat(k, v) }
                    export.stringSets.filterKeys { it !in DEVICE_LOCAL_KEYS }.forEach { (k, v) -> putStringSet(k, v) }
                }
                SECURE_CREDENTIAL_KEYS.forEach { key ->
                    check(sharedPreferences.edit().remove(key).commit()) {
                        "Plaintext credential cleanup failed"
                    }
                }

                if (export.keystore != null) {
                    val bytes = android.util.Base64.decode(export.keystore.contentBase64, android.util.Base64.NO_WRAP)
                    // Always under KEYSTORE_FILENAME, whatever name the export
                    // carries: that is the one name the backup and
                    // device-transfer rules exclude. Restoring under the exported
                    // name ("release.jks") put the signing key into cloud backups.
                    val destFile = File(context.filesDir, KEYSTORE_FILENAME)
                    FileOutputStream(destFile).use { it.write(bytes) }
                    sharedPreferences.edit { putString(KEY_KEYSTORE_PATH, destFile.absolutePath) }
                }

                withContext(Dispatchers.Main) {
                    _apiKey.value = getApiKey()
                    _currentAppName.value = getAppName()
                    _localProjects.value = getProjectList().toList()
                    _logLevel.value = getLogLevel()
                    _settingsVersion.value++
                    Toast.makeText(context, "Settings imported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Import failed: ${e.message} (Wrong password?)", Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun saveLastPrompt(prompt: String) = sharedPreferences.edit { putString(KEY_LAST_PROMPT, prompt) }
    fun getLastPrompt() = sharedPreferences.getString(KEY_LAST_PROMPT, "")

    private fun loadLocalProjects() { _localProjects.value = getProjectList().toList() }

    fun addProject(projectName: String) {
        if (projectName.isBlank()) return
        val projects = getProjectList().toMutableSet()
        projects.add(projectName)
        sharedPreferences.edit { putStringSet(KEY_PROJECT_LIST, projects) }
        loadLocalProjects()
    }

    fun removeProject(projectName: String) {
        if (projectName.isBlank()) return
        val projects = getProjectList().toMutableSet()
        projects.remove(projectName)
        sharedPreferences.edit { putStringSet(KEY_PROJECT_LIST, projects) }
        loadLocalProjects()
    }

    fun getProjectList() = sharedPreferences.getStringSet(KEY_PROJECT_LIST, emptySet()) ?: emptySet()

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun getProjectPaths(): Map<String, String> {
        val jsonStr = sharedPreferences.getString(KEY_PROJECT_PATHS, "{}")
        return try { json.decodeFromString<Map<String, String>>(jsonStr ?: "{}") } catch (e: Exception) { emptyMap() }
    }

    fun saveProjectPath(name: String, path: String) {
        val paths = getProjectPaths().toMutableMap()
        paths[name] = path
        sharedPreferences.edit { putString(KEY_PROJECT_PATHS, json.encodeToString(paths)) }
    }

    fun getProjectPath(name: String): File {
        val path = getProjectPaths()[name]
        if (!path.isNullOrBlank()) return File(path)
        return getApplication<Application>().filesDir.resolve(name)
    }

    fun removeProjectPath(name: String) {
        val paths = getProjectPaths().toMutableMap()
        if (paths.remove(name) != null) {
            sharedPreferences.edit { putString(KEY_PROJECT_PATHS, json.encodeToString(paths)) }
        }
    }

    fun saveProjectConfig(appName: String, githubUser: String, branchName: String) {
        sharedPreferences.edit { putString(KEY_APP_NAME, appName).putString(KEY_GITHUB_USER, githubUser).putString(KEY_BRANCH_NAME, branchName) }
        if (appName.isNotBlank()) addProject(appName)
    }

    fun getAppName() = sharedPreferences.getString(KEY_APP_NAME, null)

    fun saveRepoDescription(description: String) =
        sharedPreferences.edit { putString(KEY_REPO_DESCRIPTION, description) }

    fun getRepoDescription() =
        sharedPreferences.getString(KEY_REPO_DESCRIPTION, null)?.takeIf { it.isNotBlank() }
            ?: "Created with IDEaz"

    fun setAppName(appName: String) {
        sharedPreferences.edit { putString(KEY_APP_NAME, appName) }
        _currentAppName.value = appName
    }

    fun getGithubUser() = sharedPreferences.getString(KEY_GITHUB_USER, null)
    fun setGithubUser(githubUser: String) = sharedPreferences.edit { putString(KEY_GITHUB_USER, githubUser) }
    fun getBranchName() = sharedPreferences.getString(KEY_BRANCH_NAME, "main") ?: "main"
    fun saveBranchName(branchName: String) = sharedPreferences.edit { putString(KEY_BRANCH_NAME, branchName) }

    fun saveRepoPermissions(canPush: Boolean, isAdmin: Boolean) = sharedPreferences.edit { putBoolean(KEY_REPO_CAN_PUSH, canPush).putBoolean(KEY_REPO_IS_ADMIN, isAdmin) }
    fun canPushToRepo() = sharedPreferences.getBoolean(KEY_REPO_CAN_PUSH, true)

    fun getAppVersion(): String {
        return try {
            val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
            "v${pInfo.versionName}"
        } catch (e: Exception) { "Unknown" }
    }
}
