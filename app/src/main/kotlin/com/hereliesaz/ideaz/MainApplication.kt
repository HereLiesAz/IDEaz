package com.hereliesaz.ideaz

import android.app.Application
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.CrashHandler
import com.hereliesaz.ideaz.utils.ToolManager

class MainApplication : Application() {

    lateinit var mainViewModel: MainViewModel
        private set

    override fun onCreate() {
        super.onCreate()

        val settingsViewModel = SettingsViewModel(this)
        mainViewModel = MainViewModel(this, settingsViewModel)

        // CRITICAL: Force JNA to use the bundled library, NOT the system one.
        // This fixes the java.lang.Error crash on startup.
        System.setProperty("jna.nosys", "true")

        try {
            System.setProperty("jna.boot.library.path", applicationInfo.nativeLibraryDir)
        } catch (e: Exception) {
            // Ignore
        }

        // Initialize Crash Reporting
        CrashHandler.init(this)
        ToolManager.init(this)
    }
}