package com.hereliesaz.ideaz.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ProcessManifestTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun fixMissingPackageAndExported() {
        val manifestFile = tempFolder.newFile("AndroidManifest.xml")
        manifestFile.writeText("""
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                    <activity android:name=".SecondActivity" />
                </application>
            </manifest>
        """.trimIndent())

        val outputFile = File(tempFolder.root, "processed_manifest.xml")
        val outputPath = outputFile.absolutePath

        val processor = ProcessManifest(
            manifestFile.absolutePath,
            outputPath,
            packageName = "com.test.app",
            minSdk = 26,
            targetSdk = 36
        )
        val result = processor.execute(null)

        assertTrue(result.success)
        assertTrue(outputFile.exists())

        val content = outputFile.readText()

        // Check package injected
        assertTrue(content.contains("package=\"com.test.app\""))

        // Check uses-sdk injected
        assertTrue(content.contains("android:minSdkVersion=\"26\""))
        assertTrue(content.contains("android:targetSdkVersion=\"36\""))

        // Check exported injected
        // MainActivity has intent-filter -> exported=true
        // SecondActivity has no intent-filter -> exported=false

        // Since XML order/formatting might change, parsing to verify is safer, but string check works if unique.
        // Or check existence.

        // Let's rely on basic string checks for now, trusting DOM transformer produces valid XML.
        // We need to match <activity android:name=".MainActivity" ... android:exported="true">
        // The order of attributes is not guaranteed.
        // But "android:exported" must be present.

        // Check count of exported attributes
        val exportedTrueCount = content.split("android:exported=\"true\"").size - 1
        val exportedFalseCount = content.split("android:exported=\"false\"").size - 1

        assertEquals(1, exportedTrueCount) // MainActivity
        assertEquals(1, exportedFalseCount) // SecondActivity
    }

    @Test
    fun generateStubIfMissing() {
        val inputPath = File(tempFolder.root, "missing.xml").absolutePath
        val outputFile = File(tempFolder.root, "stub.xml")

        val processor = ProcessManifest(
            inputPath,
            outputFile.absolutePath,
            packageName = "com.stub.app",
            minSdk = 26,
            targetSdk = 36
        )

        val result = processor.execute(null)
        assertTrue(result.success)

        val content = outputFile.readText()
        assertTrue(content.contains("package=\"com.stub.app\""))
        assertTrue(content.contains("<application android:label=\"StubApp\" />"))
    }
}
