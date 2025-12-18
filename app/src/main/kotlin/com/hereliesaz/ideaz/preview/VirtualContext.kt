package com.hereliesaz.ideaz.preview

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.LayoutInflater

/**
 * A ContextWrapper that provides the Guest APK's resources and ClassLoader,
 * fooling the Guest Activity into thinking it's running in its own environment.
 */
class VirtualContext(
    base: Context,
    private val guestResources: Resources,
    private val guestClassLoader: ClassLoader,
    private val guestThemeId: Int
) : ContextWrapper(base) {

    private val inflater: LayoutInflater by lazy {
        LayoutInflater.from(base).cloneInContext(this)
    }

    override fun getResources(): Resources = guestResources

    override fun getAssets(): AssetManager = guestResources.assets

    override fun getClassLoader(): ClassLoader = guestClassLoader

    override fun getTheme(): Resources.Theme {
        val theme = guestResources.newTheme()
        theme.applyStyle(guestThemeId, true)
        return theme
    }

    override fun getSystemService(name: String): Any? {
        if (Context.LAYOUT_INFLATER_SERVICE == name) {
            return inflater
        }
        return super.getSystemService(name)
    }

    override fun getPackageName(): String {
        // Return host package name to prevent SecurityExceptions when accessing system services,
        // as the UID belongs to the host.
        return baseContext.packageName
    }
}