package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SimpleJsBundlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun bundle_injectsAccessibilityLabels() {
        val projectDir = tempFolder.newFolder("rn_project")
        val appJs = File(projectDir, "App.js")
        appJs.writeText("""
            import React from 'react';
            import { View, Text } from 'react-native';
            export default function App() {
                return (
                    <View style={styles.container}>
                        <Text>Hello World</Text>
                        <View />
                    </View>
                );
            }
        """.trimIndent())

        val outputDir = tempFolder.newFolder("output")
        val bundler = SimpleJsBundler()

        val result = bundler.bundle(projectDir, outputDir)
        assertTrue(result.success)

        val bundleFile = File(outputDir, "index.android.bundle")
        assertTrue(bundleFile.exists())

        val content = bundleFile.readText()

        // Check for injection
        // Line 5: <View style={styles.container}>
        assertTrue("Should inject into View on line 5", content.contains("accessibilityLabel=\"__source:App.js:5__\""))

        // Line 6: <Text>Hello World</Text>
        assertTrue("Should inject into Text on line 6", content.contains("accessibilityLabel=\"__source:App.js:6__\""))

        // Line 7: <View />
        assertTrue("Should inject into self-closing View on line 7", content.contains("accessibilityLabel=\"__source:App.js:7__\""))

        // Check Boilerplate wrapping
        assertTrue(content.contains("AppRegistry.registerComponent"))
        assertTrue(content.contains("function App()")) // export default stripped
    }
}
