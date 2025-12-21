package com.hereliesaz.ideaz

import android.app.Application
import android.util.Log
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.CrashHandler
import com.hereliesaz.ideaz.utils.ToolManager
import java.io.File
import okhttp3.OkHttpClient

class MainApplication : Application() {

    val okHttpClient by lazy { OkHttpClient() }

    lateinit var mainViewModel: MainViewModel
        private set

    override fun onCreate() {
        super.onCreate()

        // Configure JNA for Android before any other initialization
        setupJna()

        val settingsViewModel = SettingsViewModel(this)
        mainViewModel = MainViewModel(this, settingsViewModel)

        // Initialize Crash Reporting
        CrashHandler.init(this)
        ToolManager.init(this)
    }

    private fun setupJna() {
        try {
            val nativeLibDir = applicationInfo.nativeLibraryDir
            val jnaLib = File(nativeLibDir, "libjnidispatch.so")
            
            Log.d("MainApplication", "JNA: Native library dir is $nativeLibDir")
            Log.d("MainApplication", "JNA: libjnidispatch.so exists: ${jnaLib.exists()}")

            // Set JNA properties
            System.setProperty("jna.debug_load", "true")
            System.setProperty("jna.boot.library.path", nativeLibDir)
            
            // On Android, we typically want jna.nosys=false so it uses System.loadLibrary.
            // However, if there are conflicts with other libs, jna.nosys=true + boot path might be safer.
            // We'll try false first as it's the standard Android behavior.
            System.setProperty("jna.nosys", "false")

            // Explicitly try to load it to catch errors early
            try {
                System.loadLibrary("jnidispatch")
                Log.d("MainApplication", "JNA: Successfully loaded jnidispatch via System.loadLibrary")
            } catch (e: UnsatisfiedLinkError) {
                Log.w("MainApplication", "JNA: Failed to load jnidispatch via System.loadLibrary: ${e.message}")
                // If System.loadLibrary fails, JNA might still find it via boot path or resources
            }

        } catch (e: Exception) {
            Log.e("MainApplication", "JNA: Error during setup", e)
        }
    }
}
