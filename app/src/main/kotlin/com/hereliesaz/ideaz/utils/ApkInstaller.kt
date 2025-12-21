package com.hereliesaz.ideaz.utils

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.hereliesaz.ideaz.MainActivity
import java.io.File
import android.net.Uri

object ApkInstaller {

    fun installApk(context: Context, apkPath: String) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        val file = File(apkPath)
        if (!file.exists()) {
            session.close()
            return
        }

        val inputStream = file.inputStream()
        val outputStream = session.openWrite("IDEazIDE", 0, file.length())

        inputStream.copyTo(outputStream)
        session.fsync(outputStream)
        outputStream.close()
        inputStream.close()

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
            // Allow the system to start the activity even if we are in the background (BAL protection)
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        if (Build.VERSION.SDK_INT >= 35) { // VANILLA_ICE_CREAM (Android 15+)
            // Explicitly allow background activity start as creator for PendingIntent
            options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }

        val pendingIntent = PendingIntent.getActivity(context, sessionId, intent, flags, options.toBundle())
        session.commit(pendingIntent.intentSender)
        session.close()
    }

    fun installApk(context: Context, uri: android.net.Uri) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        val inputStream = context.contentResolver.openInputStream(uri) ?: return
        val outputStream = session.openWrite("IDEazIDE_uri", 0, -1) // size -1 if unknown

        inputStream.copyTo(outputStream)
        session.fsync(outputStream)
        outputStream.close()
        inputStream.close()

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        if (Build.VERSION.SDK_INT >= 35) {
            options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }

        val pendingIntent = PendingIntent.getActivity(context, sessionId, intent, flags, options.toBundle())
        session.commit(pendingIntent.intentSender)
        session.close()
    }
}
