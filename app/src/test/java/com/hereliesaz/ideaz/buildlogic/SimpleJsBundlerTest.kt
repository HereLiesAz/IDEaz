package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SimpleJsBundlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun bundle_readsAppNameFromRootJson() {
        val projectDir = tempFolder.newFolder("project_root")
        File(projectDir, "App.js").writeText("export default function App() {}")

        val appJson = File(projectDir, "app.json")
        appJson.writeText("""
            {
                "name": "RootName"
            }
        """.trimIndent())

        val bundler = SimpleJsBundler()
        val outputDir = tempFolder.newFolder("output")
        val result = bundler.bundle(projectDir, outputDir)
        assertTrue("Bundling failed: ${result.output}", result.success)

        val outFile = File(outputDir, "index.android.bundle")
        val content = outFile.readText()
        assertTrue("Should use root 'name' property", content.contains("AppRegistry.registerComponent('RootName'"))
    }

    @Test
    fun bundle_readsAppNameFromExpoJson() {
        val projectDir = tempFolder.newFolder("project_expo")
        File(projectDir, "App.js").writeText("export default function App() {}")

        val appJson = File(projectDir, "app.json")
        appJson.writeText("""
            {
                "expo": {
                    "name": "ExpoName"
                }
            }
        """.trimIndent())

        val bundler = SimpleJsBundler()
        val outputDir = tempFolder.newFolder("output")
        val result = bundler.bundle(projectDir, outputDir)
        assertTrue("Bundling failed: ${result.output}", result.success)

        val outFile = File(outputDir, "index.android.bundle")
        val content = outFile.readText()
        assertTrue("Should use expo.name property", content.contains("AppRegistry.registerComponent('ExpoName'"))
    }

    @Test
    fun bundle_prioritizesRootNameOverExpo() {
        val projectDir = tempFolder.newFolder("project_both")
        File(projectDir, "App.js").writeText("export default function App() {}")

        val appJson = File(projectDir, "app.json")
        appJson.writeText("""
            {
                "name": "RootName",
                "expo": {
                    "name": "ExpoName"
                }
            }
        """.trimIndent())

        val bundler = SimpleJsBundler()
        val outputDir = tempFolder.newFolder("output")
        val result = bundler.bundle(projectDir, outputDir)
        assertTrue("Bundling failed: ${result.output}", result.success)

        val outFile = File(outputDir, "index.android.bundle")
        val content = outFile.readText()
        assertTrue("Should prioritize root name over expo name", content.contains("AppRegistry.registerComponent('RootName'"))
    }

    @Test
    fun bundle_fallsBackToDisplayName() {
        val projectDir = tempFolder.newFolder("project_display")
        File(projectDir, "App.js").writeText("export default function App() {}")

        val appJson = File(projectDir, "app.json")
        appJson.writeText("""
            {
                "displayName": "DisplayApp"
            }
        """.trimIndent())

        val bundler = SimpleJsBundler()
        val outputDir = tempFolder.newFolder("output")
        val result = bundler.bundle(projectDir, outputDir)
        assertTrue("Bundling failed: ${result.output}", result.success)

        val outFile = File(outputDir, "index.android.bundle")
        val content = outFile.readText()
        assertTrue("Should fallback to displayName", content.contains("AppRegistry.registerComponent('DisplayApp'"))
    }

    @Test
    fun bundle_injectsAccessibilityLabels() {
        val bundler = SimpleJsBundler()
        val input = listOf(
            "<View style={{flex:1}}>",
            "  <Text>Hello</Text>",
            "</View>"
        )
        val result = bundler.processSource(input, "Test.js")

        // Assertions for line 1
        assertTrue(
            "Line 1 View missing accessibilityLabel",
            result.contains("""<View style={{flex:1}} accessibilityLabel="__source:Test.js:1__">""")
        )

        // Assertions for line 2
        assertTrue(
            "Line 2 Text missing accessibilityLabel",
            result.contains("""<Text accessibilityLabel="__source:Test.js:2__">Hello</Text>""")
        )
    }
}
