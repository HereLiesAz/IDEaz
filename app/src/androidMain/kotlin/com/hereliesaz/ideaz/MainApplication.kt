package com.hereliesaz.ideaz

import android.app.Application
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

    override fun onTerminate() {
        // Best-effort only: the platform guarantees onTerminate() is never
        // called on a real device, only in the emulator/test harness. This
        // ViewModel is intentionally Application-scoped (constructed once
        // above, never through a ViewModelStore), so its onCleared() - which
        // unregisters SystemEventDelegate's broadcast receivers and stops the
        // file observer - was otherwise completely unreachable. Real teardown
        // still happens the same way every process-scoped resource does: the
        // OS reclaims it at process death.
        mainViewModel.releaseResources()
        super.onTerminate()
    }
}
