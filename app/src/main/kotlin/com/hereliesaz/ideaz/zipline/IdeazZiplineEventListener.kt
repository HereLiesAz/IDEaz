package com.hereliesaz.ideaz.zipline

import app.cash.zipline.EventListener
import app.cash.zipline.Zipline
import app.cash.zipline.ZiplineService
import app.cash.zipline.Call
import com.hereliesaz.ideaz.ui.LogHandler

class IdeazZiplineEventListener(
    private val logHandler: LogHandler
) : EventListener() {

    override fun serviceLeaked(zipline: Zipline, name: String) {
        logHandler.onOverlayLog("Zipline Service Leaked: $name")
    }

    override fun applicationLoadFailed(
        applicationName: String,
        manifestUrl: String?,
        exception: Exception,
        startValue: Any?
    ) {
        logHandler.onOverlayLog("Zipline App Load Failed ($applicationName): ${exception.message}")
        exception.printStackTrace()
    }

    override fun downloadFailed(applicationName: String, url: String, exception: Exception, startValue: Any?) {
        logHandler.onOverlayLog("Zipline Download Failed ($url): ${exception.message}")
    }
}
