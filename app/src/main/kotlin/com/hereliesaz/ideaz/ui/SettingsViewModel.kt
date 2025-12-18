package com.hereliesaz.ideaz.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.models.ProjectType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import android.util.Base64

// AI Model Helper
data class AiModel(val id: String, val displayName: String, val requiredKey: String)

object AiModels {
    const val JULES_DEFAULT = "JULES_DEFAULT"
    const val GEMINI_FLASH = "GEMINI_FLASH"

    val tasks = mapOf(
        "chat" to "Chat",
        "codegen" to "Code Generation",
        "debug" to "Debugging"
    )
}

@Serializable
data class SettingsExport(
    val strings: Map<String, String>,
    val booleans: Map<String, Boolean>
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // Version trigger for UI recomposition
    private val _settingsVersion = MutableStateFlow(0)
    val settingsVersion = _settingsVersion.asStateFlow()

    // --- App Config Keys ---
    companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_GITHUB_USER = "github_user"
        const val KEY_GOOGLE_API_KEY = "google_api_key"
        const val KEY_JULES_PROJECT_ID = "jules_project_id"

        const val KEY_APP_NAME = "app_name"
        const val KEY_BRANCH_NAME = "branch_name"
        const val KEY_PROJECT_LIST = "project_list"

        const val KEY_SHOW_CANCEL_WARNING = "show_cancel_warning"
        const val KEY_AUTO_REPORT_BUGS = "auto_report_bugs"
        const val KEY_AUTO_DEBUG_BUILDS = "auto_debug_builds"
        const val KEY_REPORT_IDE_ERRORS = "report_ide_errors"
        const val KEY_ENABLE_LOCAL_BUILDS = "enable_local_builds"

        const val KEY_KEYSTORE_PATH = "keystore_path"
        const val KEY_KEYSTORE_PASS = "keystore_pass"
        const val KEY_KEY_ALIAS = "key_alias"
        const val KEY_KEY_PASS = "key_pass"

        val aiTasks = AiModels.tasks
    }

    val currentAppName = MutableStateFlow(getAppName())

    // --- Getters ---
    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)
    fun getGithubToken(): String? = prefs.getString(KEY_GITHUB_TOKEN, null)
    fun getGithubUser(): String? = prefs.getString(KEY_GITHUB_USER, null)
    fun getGoogleApiKey(): String? = prefs.getString(KEY_GOOGLE_API_KEY, null)
    fun getJulesProjectId(): String? = prefs.getString(KEY_JULES_PROJECT_ID, null)

    fun getAppName(): String? = prefs.getString(KEY_APP_NAME, null)
    fun getBranchName(): String = prefs.getString(KEY_BRANCH_NAME, "main") ?: "main"

    fun getShowCancelWarning(): Boolean = prefs.getBoolean(KEY_SHOW_CANCEL_WARNING, true)
    fun getAutoReportBugs(): Boolean = prefs.getBoolean(KEY_AUTO_REPORT_BUGS, false)
    fun isAutoDebugBuildsEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_DEBUG_BUILDS, false)
    fun isReportIdeErrorsEnabled(): Boolean = prefs.getBoolean(KEY_REPORT_IDE_ERRORS, true)
    fun isLocalBuildEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_LOCAL_BUILDS, false)

    fun getKeystorePath(): String? = prefs.getString(KEY_KEYSTORE_PATH, null)
    fun getKeystorePass(): String? = prefs.getString(KEY_KEYSTORE_PASS, null)
    fun getKeyAlias(): String? = prefs.getString(KEY_KEY_ALIAS, null)
    fun getKeyPass(): String? = prefs.getString(KEY_KEY_PASS, null)

    fun getProjectList(): Set<String> = prefs.getStringSet(KEY_PROJECT_LIST, emptySet()) ?: emptySet()

    fun getAppVersion(): String = "1.5.15" // Matches version.properties

    // --- Setters ---
    fun saveApiKey(key: String) = putString(KEY_API_KEY, key)
    fun saveGithubToken(token: String) = putString(KEY_GITHUB_TOKEN, token)
    fun setGithubUser(user: String) = putString(KEY_GITHUB_USER, user)
    fun saveGoogleApiKey(key: String) = putString(KEY_GOOGLE_API_KEY, key)
    fun saveJulesProjectId(id: String) = putString(KEY_JULES_PROJECT_ID, id)

    fun setAppName(name: String) {
        putString(KEY_APP_NAME, name)
        currentAppName.value = name
    }

    fun setShowCancelWarning(value: Boolean) = putBool(KEY_SHOW_CANCEL_WARNING, value)
    fun setAutoReportBugs(value: Boolean) = putBool(KEY_AUTO_REPORT_BUGS, value)
    fun setAutoDebugBuildsEnabled(value: Boolean) = putBool(KEY_AUTO_DEBUG_BUILDS, value)
    fun setReportIdeErrorsEnabled(value: Boolean) = putBool(KEY_REPORT_IDE_ERRORS, value)
    fun setLocalBuildEnabled(value: Boolean) = putBool(KEY_ENABLE_LOCAL_BUILDS, value)

    fun saveSigningCredentials(pass: String, alias: String, keyPass: String) {
        prefs.edit()
            .putString(KEY_KEYSTORE_PASS, pass)
            .putString(KEY_KEY_ALIAS, alias)
            .putString(KEY_KEY_PASS, keyPass)
            .apply()
        _settingsVersion.value += 1
    }

    fun clearSigningConfig() {
        prefs.edit()
            .remove(KEY_KEYSTORE_PATH)
            .remove(KEY_KEYSTORE_PASS)
            .remove(KEY_KEY_ALIAS)
            .remove(KEY_KEY_PASS)
            .apply()
        _settingsVersion.value += 1
    }

    // --- Project Management ---
    fun addProject(name: String, path: String) {
        // Not used directly by UI in new architecture, mostly by RepoDelegate
        // But requested by errors in previous turns
    }

    fun removeProject(name: String) {
        // Implementation handled in RepoDelegate primarily, but removing from list here:
        val list = getProjectList().toMutableSet()
        // Assuming list contains names or paths
        list.remove(name)
        prefs.edit().putStringSet(KEY_PROJECT_LIST, list).apply()
    }

    fun saveProjectConfig(name: String, user: String, branch: String) {
        prefs.edit()
            .putString(KEY_APP_NAME, name)
            .putString(KEY_GITHUB_USER, user)
            .putString(KEY_BRANCH_NAME, branch)
            .apply()
        currentAppName.value = name
    }

    fun checkRequiredKeys(): List<String> {
        val missing = mutableListOf<String>()
        if (getGithubToken().isNullOrBlank()) missing.add("GitHub Token")
        // Add others as needed
        return missing
    }

    // --- Import/Export ---
    fun exportSettings(context: Context, uri: Uri, password: String) {
        // Basic export logic placeholder matching signature
        val all = prefs.all
        val strings = all.filterValues { it is String }.mapValues { it.value as String }
        val bools = all.filterValues { it is Boolean }.mapValues { it.value as Boolean }
        val export = SettingsExport(strings, bools)
        val jsonStr = json.encodeToString(export)
        context.contentResolver.openOutputStream(uri)?.use { it.write(jsonStr.toByteArray()) }
    }

    fun importSettings(context: Context, uri: Uri, password: String) {
        context.contentResolver.openInputStream(uri)?.use {
            val jsonStr = it.bufferedReader().readText()
            val export = json.decodeFromString<SettingsExport>(jsonStr)
            val editor = prefs.edit()
            export.strings.forEach { (k, v) -> editor.putString(k, v) }
            export.booleans.forEach { (k, v) -> editor.putBoolean(k, v) }
            editor.apply()
            _settingsVersion.value += 1
        }
    }

    fun importKeystore(context: Context, uri: Uri): String? {
        // Placeholder for keystore copy logic
        return "imported_keystore"
    }

    // --- AI Assignment ---
    fun getAiAssignment(task: String): String? = prefs.getString("AI_TASK_$task", null)
    fun saveAiAssignment(task: String, model: String) = putString("AI_TASK_$task", model)

    // --- Helpers ---
    private fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        _settingsVersion.value += 1
    }

    private fun putBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        _settingsVersion.value += 1
    }
}