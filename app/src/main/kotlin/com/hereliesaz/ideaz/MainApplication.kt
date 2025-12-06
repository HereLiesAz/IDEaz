package com.hereliesaz.ideaz

import android.app.Application
import com.hereliesaz.ideaz.utils.CrashHandler
import com.hereliesaz.ideaz.utils.ToolManager

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // CRITICAL: Force JNA to use the bundled library, NOT the system one.
        // This fixes the java.lang.Error crash on startup.
        System.setProperty("jna.nosys", "true")

        try {
            System.setProperty("jna.boot.library.path", applicationInfo.nativeLibraryDir)
        } catch (e: Exception) {
            // Ignore
        }

        CrashHandler.init(this)
        ToolManager.init(this)
    }
}