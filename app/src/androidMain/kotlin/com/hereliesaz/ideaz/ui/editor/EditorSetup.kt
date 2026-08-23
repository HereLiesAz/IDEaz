package com.hereliesaz.ideaz.ui.editor

import android.content.Context
import android.util.Log
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver

object EditorSetup {
    private var initialized = false
    private const val TAG = "EditorSetup"

    fun ensureInitialized(context: Context) {
        if (initialized) return

        val appContext = context.applicationContext

        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(appContext.assets)
        )

        try {
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load grammars", e)
        }

        initialized = true
    }

    fun createLanguage(fileExtension: String): TextMateLanguage {
        val scopeName = when (fileExtension) {
            "html" -> "text.html.basic"
            "js" -> "source.js"
            "css" -> "source.css"
            else -> "text.html.basic"
        }
        return TextMateLanguage.create(scopeName, true)
    }
}
