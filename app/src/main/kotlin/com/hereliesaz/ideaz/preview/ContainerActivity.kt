package com.hereliesaz.ideaz.preview

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

class ContainerActivity : ComponentActivity() {
    companion object { const val EXTRA_APK_PATH = "apk_path" }
    private var guestActivity: Activity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        if (apkPath == null) {
            setContentView(TextView(this).apply { text = "Error: No APK provided." })
            return
        }

        try {
            val loadedApk = ApkLoader.loadApk(this, apkPath)
            if (loadedApk.mainActivityClassName == null) throw RuntimeException("No Activity found.")

            val virtualContext = VirtualContext(this, loadedApk.resources, loadedApk.classLoader, loadedApk.themeId)
            val activityClass = loadedApk.classLoader.loadClass(loadedApk.mainActivityClassName)
            guestActivity = activityClass.newInstance() as Activity

            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(guestActivity, virtualContext)

            setField(Activity::class.java, guestActivity!!, "mWindow", this.window)
            setField(Activity::class.java, guestActivity!!, "mWindowManager", this.windowManager)
            setField(Activity::class.java, guestActivity!!, "mInstrumentation", Instrumentation())
            setField(Activity::class.java, guestActivity!!, "mApplication", this.application)

            val onCreateMethod = Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java)
            onCreateMethod.isAccessible = true
            onCreateMethod.invoke(guestActivity, savedInstanceState)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setField(clazz: Class<*>, instance: Any, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(instance, value)
        } catch (e: Exception) { e.printStackTrace() }
    }
}