package com.hereliesaz.ideaz.react

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class IdeazReactPackage : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(IdeazNativeModule(reactContext))
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList()
    }
}
