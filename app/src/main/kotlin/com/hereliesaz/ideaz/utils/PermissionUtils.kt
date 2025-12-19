package com.hereliesaz.ideaz.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast

/**
 * Checks for storage permissions (All Files Access on Android R+) and executes the action if granted.
 * If not granted, it prompts the user to enable the permission.
 */
fun checkAndRequestStoragePermission(context: Context, onGranted: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (Environment.isExternalStorageManager()) {
            onGranted()
        } else {
            Toast.makeText(context, "Please allow 'All Files Access' to continue", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
    } else {
        // For Android < 11, permissions are typically handled via runtime permission requests (READ_EXTERNAL_STORAGE).
        // The user specifically asked for "all files" check which refers to MANAGE_EXTERNAL_STORAGE.
        onGranted()
    }
}
