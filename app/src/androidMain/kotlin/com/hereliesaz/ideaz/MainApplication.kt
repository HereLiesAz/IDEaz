package com.hereliesaz.ideaz

import android.app.Application
import kotlinx.coroutines.launch
import com.hereliesaz.ideaz.ui.MainViewModel
import com.hereliesaz.ideaz.ui.SettingsViewModel

class MainApplication : Application() {

    lateinit var mainViewModel: MainViewModel
        private set

    override fun onCreate() {
        super.onCreate()
        // Before any ViewModel: AiModels reads installed store models from it.
        com.hereliesaz.ideaz.azphalt.AzphaltStore.init(this)
        recheckStoreRevocations()

        val settingsViewModel = SettingsViewModel(this)
        mainViewModel = MainViewModel(this, settingsViewModel)
    }

    /** Switches off installed store models revoked since install. Offline or failing: nothing changes. */
    private fun recheckStoreRevocations() {
        val store = com.hereliesaz.ideaz.azphalt.AzphaltStore
        if (store.llms(this).all().isEmpty()) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { store.service(this@MainApplication).recheckRevocations() }
        }
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
