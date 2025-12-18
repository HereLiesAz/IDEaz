package com.hereliesaz.ideaz.services

import android.app.Service
import android.content.Intent
import android.graphics.Rect
import android.os.IBinder

class IdeazOverlayService : Service() {

    private var activeSelectionRect: Rect? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Overlay initialization logic would be here
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}