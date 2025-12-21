package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReactNativeBuildStepTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var assetsDir: File
    private lateinit var buildStep: ReactNativeBuildStep

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("project")
        assetsDir = tempFolder.newFolder("output_assets")
        buildStep = ReactNativeBuildStep(projectDir, assetsDir)
    }

    @Test
    fun `execute copies assets and bundles successfully`() {
        // Setup project
        File(projectDir, "app.json").writeText("""{"name": "TestApp"}""")
        File(projectDir, "App.js").writeText("const App = () => {}; export default App;")

        // Setup assets
        val projectAssets = File(projectDir, "assets")
        projectAssets.mkdirs()
        File(projectAssets, "test.html").writeText("<html></html>")

        val result = buildStep.execute(object : IBuildCallback.Stub() {
            override fun onLog(message: String) { println(message) }
            override fun onSuccess(apkPath: String) {}
            override fun onFailure(message: String) {}
        })

        assertTrue("Build step should succeed: ${result.output}", result.success)
        assertTrue("Bundle should exist", File(assetsDir, "index.android.bundle").exists())
        assertTrue("Asset file should be copied", File(assetsDir, "test.html").exists())
    }
}
