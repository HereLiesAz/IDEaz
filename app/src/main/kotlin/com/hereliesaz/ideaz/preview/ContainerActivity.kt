package com.hereliesaz.ideaz.preview

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * The Container Activity.
 * It loads a guest APK and runs its Main Activity within this window.
 */
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
            // 1. Load the Guest APK
            val loadedApk = ApkLoader.loadApk(this, apkPath)

            if (loadedApk.mainActivityClassName == null) {
                throw RuntimeException("No Activity found in the provided APK.")
            }

            // 2. Create the Proxy Context
            val virtualContext = VirtualContext(
                this,
                loadedApk.resources,
                loadedApk.classLoader,
                loadedApk.themeId
            )

            // 3. Instantiate the Guest Activity
            val activityClass = loadedApk.classLoader.loadClass(loadedApk.mainActivityClassName)
            val instance = activityClass.newInstance() as Activity
            guestActivity = instance

            // 4. Inject Dependencies (The "Attach" Hack)

            // Inject Base Context
            val setBaseContextMethod = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
            setBaseContextMethod.isAccessible = true
            setBaseContextMethod.invoke(instance, virtualContext)

            // Inject Window (Give it OUR window)
            val mWindowField = Activity::class.java.getDeclaredField("mWindow")
            mWindowField.isAccessible = true
            mWindowField.set(instance, this.window)

            // Inject WindowManager
            val mWindowManagerField = Activity::class.java.getDeclaredField("mWindowManager")
            mWindowManagerField.isAccessible = true
            mWindowManagerField.set(instance, this.windowManager)

            // Inject Instrumentation
            val mInstrumentationField = Activity::class.java.getDeclaredField("mInstrumentation")
            mInstrumentationField.isAccessible = true
            val instrumentation = android.app.Instrumentation()
            mInstrumentationField.set(instance, instrumentation)

            // 5. Awaken the Guest (Call onCreate)
            val onCreateMethod = Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java)
            onCreateMethod.isAccessible = true
            onCreateMethod.invoke(instance, null)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Container Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // --- Lifecycle Forwarding ---

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
                // Squelch lifecycle errors if the guest doesn't implement them or they are protected
            }
        }
    }
}