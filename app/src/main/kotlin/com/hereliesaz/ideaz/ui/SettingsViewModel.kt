package com.hereliesaz.ideaz.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.AuthInterceptor

// Define AI models and their requirements
data class AiModel(val id: String, val displayName: String, val requiredKey: String)

object AiModels {
    const val JULES_DEFAULT = "JULES_DEFAULT"
    const val GEMINI_FLASH = "GEMINI_FLASH"
    const val GEMINI_PRO = "GEMINI_PRO"

    val JULES = AiModel(JULES_DEFAULT, "Jules", SettingsViewModel.KEY_API_KEY)
    val GEMINI = AiModel(GEMINI_FLASH, "Gemini Flash", SettingsViewModel.KEY_GOOGLE_API_KEY)
    // Add more models as needed
    // val GEMINI_PRO_MODEL = AiModel(GEMINI_PRO, "Gemini Pro", SettingsViewModel.KEY_GOOGLE_API_KEY)

    val availableModels = listOf(JULES, GEMINI) //, GEMINI_PRO_MODEL)

    fun findById(id: String?): AiModel? = availableModels.find { it.id == id }
}


class SettingsViewModel : ViewModel() {

    companion object {
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

        val aiTasks = mapOf(
            KEY_AI_ASSIGNMENT_DEFAULT to "Default",
            KEY_AI_ASSIGNMENT_INIT to "Project Initialization",
            KEY_AI_ASSIGNMENT_CONTEXTLESS to "Contextless Chat",
            KEY_AI_ASSIGNMENT_OVERLAY to "Overlay Chat"
        )
    }

    // --- Cancel Warning ---

    fun getShowCancelWarning(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getBoolean(KEY_SHOW_CANCEL_WARNING, true) // Default to true
    }

    fun setShowCancelWarning(context: Context, show: Boolean) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().putBoolean(KEY_SHOW_CANCEL_WARNING, show).apply()
    }

    // --- API Key Save/Get ---

    fun saveGoogleApiKey(context: Context, apiKey: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().putString(KEY_GOOGLE_API_KEY, apiKey).apply()
    }

    fun getGoogleApiKey(context: Context): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(KEY_GOOGLE_API_KEY, null)
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()

        // Also update the interceptor immediately
        AuthInterceptor.apiKey = apiKey
    }

    fun getApiKey(context: Context): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    fun getApiKey(context: Context, keyName: String): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(keyName, null)
    }

    // --- AI Assignment Save/Get ---

    fun saveAiAssignment(context: Context, taskKey: String, modelId: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().putString(taskKey, modelId).apply()
    }

    /**
     * Gets the assigned model for a task.
     * If the task is not "Default" and has no specific assignment,
     * it falls back to the "Default" assignment.
     */
    fun getAiAssignment(context: Context, taskKey: String): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultModelId = sharedPreferences.getString(KEY_AI_ASSIGNMENT_DEFAULT, AiModels.JULES_DEFAULT)

        if (taskKey == KEY_AI_ASSIGNMENT_DEFAULT) {
            return defaultModelId
        }

        // Fallback logic: Use specific, or if null, use default
        return sharedPreferences.getString(taskKey, defaultModelId)
    }

    // --- Target Package Name ---

    fun saveTargetPackageName(context: Context, packageName: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().putString(KEY_TARGET_PACKAGE_NAME, packageName).apply()

        // Send a broadcast to notify the running service of the change
        val intent = Intent(ACTION_TARGET_PACKAGE_CHANGED).apply {
            putExtra("PACKAGE_NAME", packageName)
        }
        context.sendBroadcast(intent)
    }

    fun getTargetPackageName(context: Context): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        // Default to the template package name if nothing is set
        return sharedPreferences.getString(KEY_TARGET_PACKAGE_NAME, "com.example.helloworld")
    }


    // --- Project Config (Unchanged) ---

    fun saveProjectConfig(context: Context, appName: String, githubUser: String, branchName: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit()
            .putString(KEY_APP_NAME, appName)
            .putString(KEY_GITHUB_USER, githubUser)
            .putString(KEY_BRANCH_NAME, branchName)
            .apply()

        // Also add this project to the list
        addProjectToList(context, appName, githubUser)
    }

    private fun addProjectToList(context: Context, appName: String, githubUser: String) {
        if (appName.isBlank() || githubUser.isBlank()) return

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val projects = getProjectList(context).toMutableSet()
        projects.add("$githubUser/$appName")
        sharedPreferences.edit().putStringSet(KEY_PROJECT_LIST, projects).apply()
    }

    fun getProjectList(context: Context): Set<String> {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getStringSet(KEY_PROJECT_LIST, emptySet()) ?: emptySet()
    }

    fun getAppName(context: Context): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(KEY_APP_NAME, null)
    }

    fun getGithubUser(context: Context): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(KEY_GITHUB_USER, null)
    }

    fun getBranchName(context: Context): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        // Default to "main" if nothing is saved
        return sharedPreferences.getString(KEY_BRANCH_NAME, "main")!!
    }
}