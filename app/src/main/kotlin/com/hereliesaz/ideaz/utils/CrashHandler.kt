package com.hereliesaz.ideaz.utils

import android.content.Context
import android.util.Log
import kotlin.system.exitProcess

object CrashHandler {
    private const val TAG = "CrashHandler"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(context, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable) ?: exitProcess(1)
        }
    }

    private fun handleCrash(context: Context, thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Fatal Crash on thread ${thread.name}", throwable)
        // Future: Save stacktrace to file for Jules to analyze on next boot
    }
}