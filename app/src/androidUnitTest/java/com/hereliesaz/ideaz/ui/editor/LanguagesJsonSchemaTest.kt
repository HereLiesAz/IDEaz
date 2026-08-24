package com.hereliesaz.ideaz.ui.editor

import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver
import io.github.rosemoe.sora.langs.textmate.registry.reader.LanguageDefinitionReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Verifies `assets/textmate/languages.json` against the real shipped
 * sora-editor library, not a mock of its schema.
 *
 * `LanguageDefinitionReader` (decompiled: its wrapper's `grammarDefinition`
 * field carries `@SerializedName("languages")`, and its per-entry
 * deserializer reads "grammar" and "name" - not "path"/"language") expects
 * `{"languages": [{"scopeName", "grammar", "name"}, ...]}`. The file used to
 * be `{"grammars": [{"scopeName", "path", "language"}, ...]}` - every key
 * wrong except scopeName. Gson silently returns a null list for the
 * unmatched top-level key, and LanguageDefinitionReader.read() returns that
 * null directly with no guard, so this didn't just mean zero grammars
 * loaded - it threw a NullPointerException on every single app launch,
 * silently caught by EditorSetup's own try/catch. Syntax highlighting has
 * never worked, for any file, ever.
 */
class LanguagesJsonSchemaTest {

    @Test
    fun languagesJson_parsesIntoTheGrammarDefinitionsSoraEditorActuallyExpects() {
        val assetsDir = resolveTextmateAssetsDir()
        val resolver = object : FileResolver {
            override fun resolveStreamByPath(path: String): InputStream {
                val name = path.substringAfterLast('/')
                return FileInputStream(File(assetsDir, name))
            }
        }
        FileProviderRegistry.getInstance().addFileProvider(resolver)
        try {
            val defs = LanguageDefinitionReader.read("languages.json")
            assertEquals(3, defs.size)
            val byScope = defs.associateBy { it.scopeName }
            assertEquals("html", byScope.getValue("text.html.basic").name)
            assertEquals("javascript", byScope.getValue("source.js").name)
            assertEquals("css", byScope.getValue("source.css").name)
        } finally {
            FileProviderRegistry.getInstance().removeFileProvider(resolver)
        }
    }

    private fun resolveTextmateAssetsDir(): File {
        // Robolectric unit tests run with the module directory (app/) as the
        // working directory.
        val dir = File("src/androidMain/assets/textmate")
        assertTrue("expected to find ${dir.absolutePath}", dir.isDirectory)
        return dir
    }
}
