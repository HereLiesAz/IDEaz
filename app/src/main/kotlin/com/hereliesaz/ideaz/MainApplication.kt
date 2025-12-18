package com.hereliesaz.ideaz

import android.app.Application
import com.hereliesaz.ideaz.utils.CrashHandler

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
    }
}