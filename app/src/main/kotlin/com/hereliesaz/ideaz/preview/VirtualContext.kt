package com.hereliesaz.ideaz.preview

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.LayoutInflater

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

    // We keep the host package name to avoid UID mismatches with system services
    // while the resources are swapped out above.
    override fun getPackageName(): String {
        return baseContext.packageName
    }
}