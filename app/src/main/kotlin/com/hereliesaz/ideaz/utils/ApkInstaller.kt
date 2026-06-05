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
        val file = File(apkPath)
        if (!file.exists()) return

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        packageInstaller.openSession(sessionId).use { session ->
            file.inputStream().use { inputStream ->
                session.openWrite("IDEazIDE", 0, file.length()).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    session.fsync(outputStream)
                }
            }
            session.commit(buildInstallIntentSender(context, sessionId))
        }
    }

    fun installApk(context: Context, uri: android.net.Uri) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        packageInstaller.openSession(sessionId).use { session ->
            val input = context.contentResolver.openInputStream(uri) ?: return
            input.use { inputStream ->
                session.openWrite("IDEazIDE_uri", 0, -1).use { outputStream -> // size -1 if unknown
                    inputStream.copyTo(outputStream)
                    session.fsync(outputStream)
                }
            }
            session.commit(buildInstallIntentSender(context, sessionId))
        }
    }

    private fun buildInstallIntentSender(context: Context, sessionId: Int): android.content.IntentSender {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getActivity(context, sessionId, intent, flags).intentSender
    }
}
