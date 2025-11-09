package com.hereliesaz.ideaz

import android.app.Application
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.ui.SettingsViewModel

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val apiKey = sharedPreferences.getString(SettingsViewModel.KEY_API_KEY, null)
        if (apiKey != null) {
            AuthInterceptor.apiKey = apiKey
        }
    }
}