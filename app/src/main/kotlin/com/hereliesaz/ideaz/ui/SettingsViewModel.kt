package com.hereliesaz.ideaz.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager

class SettingsViewModel : ViewModel() {

    fun saveApiKey(context: Context, apiKey: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().putString("api_key", apiKey).apply()
    }

    fun getApiKey(context: Context): String? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getString("api_key", null)
    }
}
