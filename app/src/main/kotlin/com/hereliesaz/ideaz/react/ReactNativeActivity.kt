package com.hereliesaz.ideaz.react

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.facebook.react.ReactInstanceManager
import com.facebook.react.ReactRootView
import com.facebook.react.common.LifecycleState
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler
import com.facebook.react.shell.MainReactPackage
import com.facebook.soloader.SoLoader
import com.hereliesaz.ideaz.BuildConfig
import java.io.File

/**
 * React Native Runner Activity.
 * Hosted by the IDE to run user RN projects locally.
 */
class ReactNativeActivity : AppCompatActivity(), DefaultHardwareBackBtnHandler {
    private var mReactRootView: ReactRootView? = null
    private var mReactInstanceManager: ReactInstanceManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoLoader.init(this, false)

        mReactRootView = ReactRootView(this)

        // Get bundle path from intent
        val bundlePath = intent.getStringExtra("BUNDLE_PATH")
        val moduleName = intent.getStringExtra("MODULE_NAME") ?: "MyReactNativeApp"

        if (bundlePath != null) {
             val bundleFile = File(bundlePath)
             if (!bundleFile.exists()) {
                 // Fallback or error? For now, finish if explicit path is invalid
                 // But maybe we are in development mode connecting to packager?
                 // For now assume bundled.
             }
        }

        val builder = ReactInstanceManager.builder()
            .setApplication(application)
            .setCurrentActivity(this)
            .setBundleAssetName("index.android.bundle")
            .setJSMainModulePath("index")
            .addPackage(MainReactPackage())
            .addPackage(IdeazReactPackage())
            .setUseDeveloperSupport(BuildConfig.DEBUG)
            .setInitialLifecycleState(LifecycleState.RESUMED)

        if (bundlePath != null) {
            builder.setJSBundleFile(bundlePath)
        }

        mReactInstanceManager = builder.build()

        mReactRootView?.startReactApplication(mReactInstanceManager, moduleName, null)

        setContentView(mReactRootView)
    }

    override fun invokeDefaultOnBackPressed() {
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        mReactInstanceManager?.onHostPause(this)
    }

    override fun onResume() {
        super.onResume()
        mReactInstanceManager?.onHostResume(this, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mReactInstanceManager?.onHostDestroy(this)
        mReactRootView?.unmountReactApplication()
    }

    override fun onBackPressed() {
        if (mReactInstanceManager != null) {
            mReactInstanceManager?.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }
}
