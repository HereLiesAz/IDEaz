package com.hereliesaz.ideaz.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.io.FileOutputStream
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver

// Define AI models and their requirements
data class AiModel(val id: String, val displayName: String, val requiredKey: String)

object AiModels {
    const val JULES_DEFAULT = "JULES_DEFAULT"
    const val GEMINI_FLASH = "GEMINI_FLASH"
    const val GEMINI_PRO = "GEMINI_PRO"
    const val GEMINI_CLI = "GEMINI_CLI"

    val JULES = AiModel(JULES_DEFAULT, "Jules", SettingsViewModel.KEY_API_KEY)
    val GEMINI = AiModel(GEMINI_FLASH, "Gemini Flash", SettingsViewModel.KEY_GOOGLE_API_KEY)
    val CLI = AiModel(GEMINI_CLI, "Gemini CLI", SettingsViewModel.KEY_GOOGLE_API_KEY)

    val availableModels = listOf(JULES, GEMINI, CLI)

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

    companion object {
        private const val TAG = "SettingsViewModel"
        const val KEY_API_KEY = "api_key" // Jules
        const val KEY_APP_NAME = "app_name"
        const val KEY_GITHUB_USER = "github_user"
        const val KEY_BRANCH_NAME = "branch_name"
        const val KEY_PROJECT_LIST = "project_list"
        const val KEY_PROJECT_PATHS = "project_paths"
        const val KEY_GOOGLE_API_KEY = "google_api_key" // Gemini
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_JULES_PROJECT_ID = "jules_project_id"

        const val KEY_PROJECT_TYPE = "project_type"
        const val KEY_TARGET_PACKAGE_NAME = "target_package_name"
        const val ACTION_TARGET_PACKAGE_CHANGED = "com.hereliesaz.ideaz.TARGET_PACKAGE_CHANGED"

        const val KEY_REPO_CAN_PUSH = "repo_can_push"
        const val KEY_REPO_IS_ADMIN = "repo_is_admin"
        const val KEY_BRANCH_PROTECTED = "branch_protected"
        const val KEY_PR_REQUIRED = "pr_required"

        const val KEY_AI_ASSIGNMENT_DEFAULT = "ai_assignment_default"
        const val KEY_AI_ASSIGNMENT_INIT = "ai_assignment_init"
        const val KEY_AI_ASSIGNMENT_CONTEXTLESS = "ai_assignment_contextless"
        const val KEY_AI_ASSIGNMENT_OVERLAY = "ai_assignment_overlay"

        const val KEY_SHOW_CANCEL_WARNING = "show_cancel_warning"
        const val KEY_AUTO_REPORT_BUGS = "auto_report_bugs"
        const val KEY_ENABLE_LOCAL_BUILDS = "enable_local_builds"
        const val KEY_AUTO_DEBUG_BUILDS = "auto_debug_builds"
        const val KEY_REPORT_IDE_ERRORS = "report_ide_errors"

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
        const val KEY_KEYSTORE_PASS = "keystore_pass"
        const val KEY_KEY_ALIAS = "key_alias"
        const val KEY_KEY_PASS = "key_pass"

        val aiTasks = mapOf(
            KEY_AI_ASSIGNMENT_DEFAULT to "Default",
            KEY_AI_ASSIGNMENT_INIT to "Project Initialization",
            KEY_AI_ASSIGNMENT_CONTEXTLESS to "Contextless Chat",
            KEY_AI_ASSIGNMENT_OVERLAY to "Overlay Chat"
        )
    }

    private val _localProjects = MutableStateFlow<List<String>>(emptyList())
    val localProjects = _localProjects.asStateFlow()

    private val _currentAppName = MutableStateFlow(getAppName())
    val currentAppName = _currentAppName.asStateFlow()

    private val _targetPackageName = MutableStateFlow(getTargetPackageName())
    val targetPackageName = _targetPackageName.asStateFlow()

    private val _projectType = MutableStateFlow(getProjectType())
    val projectType = _projectType.asStateFlow()

    private val _apiKey = MutableStateFlow(getApiKey())
    val apiKey = _apiKey.asStateFlow()

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
        val savedKey = getApiKey()
        if (savedKey != null) {
            AuthInterceptor.apiKey = savedKey
        }
        loadLocalProjects()
        _themeMode.value = getThemeMode()
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    // --- KEYS CHECK ---
    fun checkRequiredKeys(): List<String> {
        val missing = mutableListOf<String>()
        if (getApiKey().isNullOrBlank()) missing.add("Jules API Key")
        if (getGithubToken().isNullOrBlank()) missing.add("GitHub Token")
        return missing
    }

    // --- GETTERS/SETTERS ---

    fun getShowCancelWarning() = sharedPreferences.getBoolean(KEY_SHOW_CANCEL_WARNING, true)
    fun setShowCancelWarning(show: Boolean) = sharedPreferences.edit().putBoolean(KEY_SHOW_CANCEL_WARNING, show).apply()

    fun getAutoReportBugs() = sharedPreferences.getBoolean(KEY_AUTO_REPORT_BUGS, true)
    fun setAutoReportBugs(enabled: Boolean) = sharedPreferences.edit().putBoolean(KEY_AUTO_REPORT_BUGS, enabled).apply()

    fun isAutoDebugBuildsEnabled() = sharedPreferences.getBoolean(KEY_AUTO_DEBUG_BUILDS, true)
    fun setAutoDebugBuildsEnabled(enabled: Boolean) = sharedPreferences.edit().putBoolean(KEY_AUTO_DEBUG_BUILDS, enabled).apply()

    fun isReportIdeErrorsEnabled() = sharedPreferences.getBoolean(KEY_REPORT_IDE_ERRORS, true)
    fun setReportIdeErrorsEnabled(enabled: Boolean) = sharedPreferences.edit().putBoolean(KEY_REPORT_IDE_ERRORS, enabled).apply()

    fun isLocalBuildEnabled() = sharedPreferences.getBoolean(KEY_ENABLE_LOCAL_BUILDS, false)
    fun setLocalBuildEnabled(enabled: Boolean) = sharedPreferences.edit().putBoolean(KEY_ENABLE_LOCAL_BUILDS, enabled).apply()

    fun getThemeMode() = sharedPreferences.getString(KEY_THEME_MODE, THEME_AUTO) ?: THEME_AUTO
    fun setThemeMode(mode: String) {
        sharedPreferences.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun getLogLevel() = sharedPreferences.getString(KEY_LOG_LEVEL, LOG_LEVEL_INFO) ?: LOG_LEVEL_INFO
    fun setLogLevel(level: String) {
        sharedPreferences.edit().putString(KEY_LOG_LEVEL, level).apply()
        _logLevel.value = level
    }

    fun saveGoogleApiKey(apiKey: String) = sharedPreferences.edit().putString(KEY_GOOGLE_API_KEY, apiKey.trim()).apply()
    fun getGoogleApiKey() = sharedPreferences.getString(KEY_GOOGLE_API_KEY, null)

    fun saveGithubToken(token: String) = sharedPreferences.edit().putString(KEY_GITHUB_TOKEN, token.trim()).apply()
    fun getGithubToken() = sharedPreferences.getString(KEY_GITHUB_TOKEN, null)

    fun saveJulesProjectId(projectId: String) = sharedPreferences.edit().putString(KEY_JULES_PROJECT_ID, projectId.trim()).apply()
    fun getJulesProjectId() = sharedPreferences.getString(KEY_JULES_PROJECT_ID, null)

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        sharedPreferences.edit().putString(KEY_API_KEY, trimmed).apply()
        AuthInterceptor.apiKey = trimmed
        _apiKey.value = trimmed
    }
    fun getApiKey() = sharedPreferences.getString(KEY_API_KEY, null)
    fun getApiKey(keyName: String) = sharedPreferences.getString(keyName, null)

    fun saveAiAssignment(taskKey: String, modelId: String) = sharedPreferences.edit().putString(taskKey, modelId).apply()
    fun getAiAssignment(taskKey: String): String? {
        val defaultModelId = sharedPreferences.getString(KEY_AI_ASSIGNMENT_DEFAULT, AiModels.JULES_DEFAULT)
        if (taskKey == KEY_AI_ASSIGNMENT_DEFAULT) return defaultModelId
        return sharedPreferences.getString(taskKey, defaultModelId)
    }

    // --- SIGNING ---
    fun importKeystore(context: Context, uri: Uri): String? {
        return try {
            val destFile = File(context.filesDir, "user_release.keystore")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            val path = destFile.absolutePath
            sharedPreferences.edit().putString(KEY_KEYSTORE_PATH, path).apply()
            path
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import keystore", e)
            null
        }
    }
    fun saveSigningCredentials(storePass: String, alias: String, keyPass: String) {
        sharedPreferences.edit().putString(KEY_KEYSTORE_PASS, storePass).putString(KEY_KEY_ALIAS, alias).putString(KEY_KEY_PASS, keyPass).apply()
    }
    fun getKeystorePath() = sharedPreferences.getString(KEY_KEYSTORE_PATH, null)
    fun getKeystorePass() = sharedPreferences.getString(KEY_KEYSTORE_PASS, "android") ?: "android"
    fun getKeyAlias() = sharedPreferences.getString(KEY_KEY_ALIAS, "androiddebugkey") ?: "androiddebugkey"
    fun getKeyPass() = sharedPreferences.getString(KEY_KEY_PASS, "android") ?: "android"
    fun clearSigningConfig() = sharedPreferences.edit().remove(KEY_KEYSTORE_PATH).remove(KEY_KEYSTORE_PASS).remove(KEY_KEY_ALIAS).remove(KEY_KEY_PASS).apply()

    // --- EXPORT/IMPORT ---
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

                sharedPreferences.edit().apply {
                    export.strings.forEach { (k, v) -> putString(k, v) }
                    export.booleans.forEach { (k, v) -> putBoolean(k, v) }
                    export.ints.forEach { (k, v) -> putInt(k, v) }
                    export.longs.forEach { (k, v) -> putLong(k, v) }
                    export.floats.forEach { (k, v) -> putFloat(k, v) }
                    export.stringSets.forEach { (k, v) -> putStringSet(k, v) }
                }.apply()

                if (export.keystore != null) {
                    val bytes = android.util.Base64.decode(export.keystore.contentBase64, android.util.Base64.NO_WRAP)
                    val destFile = File(context.filesDir, export.keystore.filename)
                    FileOutputStream(destFile).use { it.write(bytes) }
                    sharedPreferences.edit().putString(KEY_KEYSTORE_PATH, destFile.absolutePath).apply()
                }

                withContext(Dispatchers.Main) {
                    _apiKey.value = getApiKey()
                    _currentAppName.value = getAppName()
                    _targetPackageName.value = getTargetPackageName()
                    _localProjects.value = getProjectList().toList()
                    _logLevel.value = getLogLevel()
                    val key = getApiKey()
                    if (key != null) AuthInterceptor.apiKey = key
                    _settingsVersion.value++
                    Toast.makeText(context, "Settings imported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Import failed: ${e.message} (Wrong password?)", Toast.LENGTH_LONG).show() }
            }
        }
    }

    // --- PACKAGE ---
    fun saveTargetPackageName(packageName: String) {
        sharedPreferences.edit().putString(KEY_TARGET_PACKAGE_NAME, packageName).apply()
        _targetPackageName.value = packageName
        val intent = Intent(ACTION_TARGET_PACKAGE_CHANGED).apply {
            putExtra("PACKAGE_NAME", packageName)
            setPackage(getApplication<Application>().packageName)
        }
        getApplication<Application>().sendBroadcast(intent)
    }
    fun getTargetPackageName() = sharedPreferences.getString(KEY_TARGET_PACKAGE_NAME, "com.example.helloworld")

    // --- PROJECTS ---
    private fun loadLocalProjects() { _localProjects.value = getProjectList().toList() }
    fun addProject(projectName: String) {
        if (projectName.isBlank()) return
        val projects = getProjectList().toMutableSet()
        projects.add(projectName)
        sharedPreferences.edit().putStringSet(KEY_PROJECT_LIST, projects).apply()
        loadLocalProjects()
    }
    fun removeProject(projectName: String) {
        if (projectName.isBlank()) return
        val projects = getProjectList().toMutableSet()
        projects.remove(projectName)
        sharedPreferences.edit().putStringSet(KEY_PROJECT_LIST, projects).apply()
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
        sharedPreferences.edit().putString(KEY_PROJECT_PATHS, json.encodeToString(paths)).apply()
    }
    fun getProjectPath(name: String): File {
        val path = getProjectPaths()[name]
        if (!path.isNullOrBlank()) return File(path)
        return getApplication<Application>().filesDir.resolve(name)
    }
    fun removeProjectPath(name: String) {
        val paths = getProjectPaths().toMutableMap()
        if (paths.remove(name) != null) {
            sharedPreferences.edit().putString(KEY_PROJECT_PATHS, json.encodeToString(paths)).apply()
        }
    }
    fun saveProjectConfig(appName: String, githubUser: String, branchName: String) {
        sharedPreferences.edit().putString(KEY_APP_NAME, appName).putString(KEY_GITHUB_USER, githubUser).putString(KEY_BRANCH_NAME, branchName).apply()
        if (appName.isNotBlank()) addProject(appName)
    }
    fun getAppName() = sharedPreferences.getString(KEY_APP_NAME, null)
    fun setAppName(appName: String) {
        sharedPreferences.edit().putString(KEY_APP_NAME, appName).apply()
        _currentAppName.value = appName
    }
    fun getGithubUser() = sharedPreferences.getString(KEY_GITHUB_USER, null)
    fun setGithubUser(githubUser: String) = sharedPreferences.edit().putString(KEY_GITHUB_USER, githubUser).apply()
    fun getBranchName() = sharedPreferences.getString(KEY_BRANCH_NAME, "main")!!
    fun saveBranchName(branchName: String) = sharedPreferences.edit().putString(KEY_BRANCH_NAME, branchName).apply()
    fun getProjectType() = sharedPreferences.getString(KEY_PROJECT_TYPE, "UNKNOWN") ?: "UNKNOWN"
    fun setProjectType(type: String) {
        sharedPreferences.edit().putString(KEY_PROJECT_TYPE, type).apply()
        _projectType.value = type
    }

    // --- REPO & VERSION ---
    fun saveRepoPermissions(canPush: Boolean, isAdmin: Boolean) = sharedPreferences.edit().putBoolean(KEY_REPO_CAN_PUSH, canPush).putBoolean(KEY_REPO_IS_ADMIN, isAdmin).apply()
    fun canPushToRepo() = sharedPreferences.getBoolean(KEY_REPO_CAN_PUSH, true)
    fun getAppVersion(): String {
        return try {
            val pInfo = getApplication<Application>().packageManager.getPackageInfo(getApplication<Application>().packageName, 0)
            "v${pInfo.versionName}"
        } catch (e: Exception) { "Unknown" }
    }
}