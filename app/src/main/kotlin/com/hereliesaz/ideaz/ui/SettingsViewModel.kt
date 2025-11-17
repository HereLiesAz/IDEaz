package com.hereliesaz.ideaz.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.hereliesaz.ideaz.api.AuthInterceptor

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


class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    companion object {
        private const val TAG = "SettingsViewModel"
        const val KEY_API_KEY = "api_key" // Jules
        const val KEY_APP_NAME = "app_name"
        const val KEY_GITHUB_USER = "github_user"
        const val KEY_BRANCH_NAME = "branch_name"
        const val KEY_PROJECT_LIST = "project_list"
        const val KEY_GOOGLE_API_KEY = "google_api_key" // Gemini

        const val KEY_TARGET_PACKAGE_NAME = "target_package_name"
        const val ACTION_TARGET_PACKAGE_CHANGED = "com.hereliesaz.ideaz.TARGET_PACKAGE_CHANGED"

        // New keys for AI assignments
        const val KEY_AI_ASSIGNMENT_DEFAULT = "ai_assignment_default"
        const val KEY_AI_ASSIGNMENT_INIT = "ai_assignment_init"
        const val KEY_AI_ASSIGNMENT_CONTEXTLESS = "ai_assignment_contextless"
        const val KEY_AI_ASSIGNMENT_OVERLAY = "ai_assignment_overlay"

        // New key for cancel warning
        const val KEY_SHOW_CANCEL_WARNING = "show_cancel_warning"

        // --- NEW: Theme Keys ---
        const val KEY_THEME_MODE = "theme_mode"
        const val THEME_AUTO = "auto"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
        const val THEME_SYSTEM = "system"
        // --- END NEW ---

        // New key for log verbosity
        const val KEY_LOG_LEVEL = "log_level"

        // Log verbosity levels
        const val LOG_LEVEL_INFO = "info"
        const val LOG_LEVEL_DEBUG = "debug"
        const val LOG_LEVEL_VERBOSE = "verbose"


        val aiTasks = mapOf(
            KEY_AI_ASSIGNMENT_DEFAULT to "Default",
            KEY_AI_ASSIGNMENT_INIT to "Project Initialization",
            KEY_AI_ASSIGNMENT_CONTEXTLESS to "Contextless Chat",
            KEY_AI_ASSIGNMENT_OVERLAY to "Overlay Chat"
        )

        // Custom Saver for the SnapshotStateMap
        val SnapshotStateMapSaver = Saver<
                androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
                Map<String, String>
                >(
            save = { it.toMap() },
            restore = { mutableStateMapOf<String, String>().apply { putAll(it) } }
        )
    }

    init {
        Log.d(TAG, "init: Creating SettingsViewModel (hash: ${this.hashCode()})")
    }

    private val _logLevel = MutableStateFlow(LOG_LEVEL_INFO)
    val logLevel = _logLevel.asStateFlow()

    // --- Cancel Warning ---

    fun getShowCancelWarning(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_CANCEL_WARNING, true) // Default to true
    }

    fun setShowCancelWarning(show: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_CANCEL_WARNING, show).apply()
    }

    // --- Theme ---

    // This function is now deprecated, use getThemeMode()
    fun isDarkMode(): Boolean {
        return getThemeMode() == THEME_DARK
    }

    // This function is now deprecated, use setThemeMode()
    fun setDarkMode(isDark: Boolean) {
        setThemeMode(if (isDark) THEME_DARK else THEME_LIGHT)
    }

    // --- NEW: Theme Get/Set ---
    fun getThemeMode(): String {
        return sharedPreferences.getString(KEY_THEME_MODE, THEME_AUTO) ?: THEME_AUTO
    }

    fun setThemeMode(mode: String) {
        sharedPreferences.edit().putString(KEY_THEME_MODE, mode).apply()
    }
    // --- END NEW ---


    // --- Log Verbosity ---

    fun getLogLevel(): String {
        return sharedPreferences.getString(KEY_LOG_LEVEL, LOG_LEVEL_INFO) ?: LOG_LEVEL_INFO
    }

    fun setLogLevel(level: String) {
        sharedPreferences.edit().putString(KEY_LOG_LEVEL, level).apply()
        _logLevel.value = level
    }


    // --- API Key Save/Get ---

    fun saveGoogleApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_GOOGLE_API_KEY, apiKey).apply()
    }

    fun getGoogleApiKey(): String? {
        return sharedPreferences.getString(KEY_GOOGLE_API_KEY, null)
    }

    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()

        // Also update the interceptor immediately
        AuthInterceptor.apiKey = apiKey
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    fun getApiKey(keyName: String): String? {
        return sharedPreferences.getString(keyName, null)
    }

    // --- AI Assignment Save/Get ---

    fun saveAiAssignment(taskKey: String, modelId: String) {
        sharedPreferences.edit().putString(taskKey, modelId).apply()
    }

    /**
     * Gets the assigned model for a task.
     * If the task is not "Default" and has no specific assignment,
     * it falls back to the "Default" assignment.
     */
    fun getAiAssignment(taskKey: String): String? {
        val defaultModelId = sharedPreferences.getString(KEY_AI_ASSIGNMENT_DEFAULT, AiModels.JULES_DEFAULT)

        if (taskKey == KEY_AI_ASSIGNMENT_DEFAULT) {
            return defaultModelId
        }

        // Fallback logic: Use specific, or if null, use default
        return sharedPreferences.getString(taskKey, defaultModelId)
    }

    // --- Target Package Name ---

    fun saveTargetPackageName(packageName: String) {
        sharedPreferences.edit().putString(KEY_TARGET_PACKAGE_NAME, packageName).apply()

        // Send a broadcast to notify the running service of the change
        val intent = Intent(ACTION_TARGET_PACKAGE_CHANGED).apply {
            putExtra("PACKAGE_NAME", packageName)
            setPackage(getApplication<Application>().packageName)
        }
        getApplication<Application>().sendBroadcast(intent)
    }

    fun getTargetPackageName(): String? {
        // Default to the template package name if nothing is set
        return sharedPreferences.getString(KEY_TARGET_PACKAGE_NAME, "com.example.helloworld")
    }


    // --- Project Config (Unchanged) ---

    fun saveProjectConfig(appName: String, githubUser: String, branchName: String) {
        sharedPreferences.edit()
            .putString(KEY_APP_NAME, appName)
            .putString(KEY_GITHUB_USER, githubUser)
            .putString(KEY_BRANCH_NAME, branchName)
            .apply()

        // Also add this project to the list
        addProjectToList(appName, githubUser)
    }

    private fun addProjectToList(appName: String, githubUser: String) {
        if (appName.isBlank() || githubUser.isBlank()) return

        val projects = getProjectList().toMutableSet()
        projects.add("$githubUser/$appName")
        sharedPreferences.edit().putStringSet(KEY_PROJECT_LIST, projects).apply()
    }

    fun getProjectList(): Set<String> {
        return sharedPreferences.getStringSet(KEY_PROJECT_LIST, emptySet()) ?: emptySet()
    }

    fun getAppName(): String? {
        return sharedPreferences.getString(KEY_APP_NAME, null)
    }

    fun getGithubUser(): String? {
        return sharedPreferences.getString(KEY_GITHUB_USER, null)
    }

    fun getBranchName(): String {
        // Default to "main" if nothing is saved
        return sharedPreferences.getString(KEY_BRANCH_NAME, "main")!!
    }
}