package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.IBuildCallback
import java.io.File

class ReactNativeBuildStep(
    private val projectDir: File,
    private val assetsDir: File
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("Starting React Native Bundle...")

        val bundler = SimpleJsBundler()
        val result = bundler.bundle(projectDir, assetsDir)

        if (result.success) {
            callback?.onLog(result.output)
            return BuildResult(true, result.output)
        } else {
            callback?.onLog("Bundling failed: ${result.output}")
            return BuildResult(false, result.output)
        }
    }
}
