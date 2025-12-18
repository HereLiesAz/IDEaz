package com.hereliesaz.ideaz.preview

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.lang.reflect.Field

class ContainerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_APK_PATH = "apk_path"
    }

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

            if (loadedApk.mainActivityClassName == null) {
                throw RuntimeException("No Activity found in the provided APK.")
            }

            val virtualContext = VirtualContext(
                this,
                loadedApk.resources,
                loadedApk.classLoader,
                loadedApk.themeId
            )

            // Instantiate Guest Activity
            val activityClass = loadedApk.classLoader.loadClass(loadedApk.mainActivityClassName)
            val instance = activityClass.newInstance() as Activity
            guestActivity = instance

            // --- THE INJECTION RITUAL ---

            // 1. Inject Context (mBase)
            val attachBaseContext = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(instance, virtualContext)

            // 2. Inject Window (Give it OUR window)
            setField(Activity::class.java, instance, "mWindow", this.window)

            // 3. Inject WindowManager
            setField(Activity::class.java, instance, "mWindowManager", this.windowManager)

            // 4. Inject Instrumentation (Standard)
            setField(Activity::class.java, instance, "mInstrumentation", Instrumentation())

            // 5. Inject Application (Ours)
            setField(Activity::class.java, instance, "mApplication", this.application)

            // 6. Awaken (Call onCreate)
            val onCreateMethod = Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java)
            onCreateMethod.isAccessible = true
            onCreateMethod.invoke(instance, savedInstanceState)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Container Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setField(clazz: Class<*>, instance: Any, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(instance, value)
        } catch (e: Exception) {
            e.printStackTrace() // Log but continue, some fields might vary by SDK
        }
    }

    // --- Lifecycle Proxying ---

    override fun onStart() {
        super.onStart()
        callLifecycleMethod("onStart")
    }

    override fun onResume() {
        super.onResume()
        callLifecycleMethod("onResume")
    }

    override fun onPause() {
        super.onPause()
        callLifecycleMethod("onPause")
    }

    override fun onStop() {
        super.onStop()
        callLifecycleMethod("onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        callLifecycleMethod("onDestroy")
    }

    private fun callLifecycleMethod(methodName: String) {
        guestActivity?.let {
            try {
                val method = Activity::class.java.getDeclaredMethod(methodName)
                method.isAccessible = true
                method.invoke(it)
            } catch (e: Exception) {
                // Ignore lifecycle syncing errors
            }
        }
    }
}