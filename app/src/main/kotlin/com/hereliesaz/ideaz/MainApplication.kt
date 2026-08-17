package com.hereliesaz.ideaz

import android.app.Application
import android.content.ComponentCallbacks2
import com.hereliesaz.ideaz.ai.local.LocalModelRuntimes
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel
import com.hereliesaz.ideaz.utils.CrashHandler

class MainApplication : Application() {

    lateinit var mainViewModel: MainViewModel
        private set

    override fun onCreate() {
        super.onCreate()

        val settingsViewModel = SettingsViewModel(this)
        mainViewModel = MainViewModel(this, settingsViewModel)

        // Initialize Crash Reporting
        CrashHandler.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            LocalModelRuntimes.requestReleaseAll()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        LocalModelRuntimes.requestReleaseAll()
    }

    override fun onTerminate() {
        // Best-effort only: the platform guarantees onTerminate() is never
        // called on a real device, only in the emulator/test harness. This
        // ViewModel is intentionally Application-scoped (constructed once
        // above, never through a ViewModelStore), so its onCleared() -
        // which unregisters SystemEventDelegate's broadcast receivers,
        // stops the file observer, and unbinds the build service - was
        // otherwise completely unreachable. Real teardown still happens the
        // same way every process-scoped resource does: the OS reclaims it
        // at process death.
        mainViewModel.releaseResources()
        super.onTerminate()
    }
}
