package com.hereliesaz.ideaz.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.hereliesaz.ideaz.MainActivity
import java.io.File

object ApkInstaller {

    fun installApk(context: Context, apkPath: String) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        val file = File(apkPath)
        if (!file.exists()) {
            // Log or handle error
            session.close()
            return
        }

        val inputStream = file.inputStream()
        val outputStream = session.openWrite("IDEazIDE", 0, file.length())

        inputStream.copyTo(outputStream)
        session.fsync(outputStream)
        outputStream.close()
        inputStream.close()

        val intent = Intent(context, MainActivity::class.java) // Or a results receiver
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getActivity(context, sessionId, intent, flags)
        session.commit(pendingIntent.intentSender)
        session.close()
    }
}