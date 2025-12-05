package com.hereliesaz.ideaz

import android.app.Application
import android.os.Build
import androidx.preference.PreferenceManager
import com.hereliesaz.ideaz.api.AuthInterceptor
import com.hereliesaz.ideaz.ui.SettingsViewModel
import org.lsposed.hiddenapibypass.HiddenApiBypass

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val apiKey = sharedPreferences.getString(SettingsViewModel.KEY_API_KEY, null)
        if (apiKey != null) {
            AuthInterceptor.apiKey = apiKey
        }
    }
}