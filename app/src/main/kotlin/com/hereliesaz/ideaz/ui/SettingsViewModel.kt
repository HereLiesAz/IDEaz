package com.hereliesaz.ideaz.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.AuthInterceptor

class SettingsViewModel : ViewModel() {

    companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_APP_NAME = "app_name"
        const val KEY_GITHUB_USER = "github_user"
        const val KEY_BRANCH_NAME = "branch_name"
        const val KEY_PROJECT_LIST = "project_list"
        const val KEY_GOOGLE_API_KEY = "google_api_key"
    }

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

    fun getApiKey(context: Context): String?
    {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

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