package com.hereliesaz.ideaz

import android.app.Application
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.CrashHandler
import com.hereliesaz.ideaz.utils.ToolManager
import okhttp3.OkHttpClient

class MainApplication : Application() {

    val okHttpClient by lazy { OkHttpClient() }

    lateinit var mainViewModel: MainViewModel
        private set

    override fun onCreate() {
        super.onCreate()

        val settingsViewModel = SettingsViewModel(this)
        mainViewModel = MainViewModel(this, settingsViewModel)

        // Initialize Crash Reporting
        CrashHandler.init(this)
        ToolManager.init(this)
    }
}
