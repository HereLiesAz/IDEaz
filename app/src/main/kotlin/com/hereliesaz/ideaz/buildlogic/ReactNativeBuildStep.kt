package com.hereliesaz.ideaz.buildlogic

import android.content.Context
import com.hereliesaz.ideaz.IBuildCallback
import com.hereliesaz.ideaz.utils.ToolManager
import java.io.File

class ReactNativeBuildStep(
    private val context: Context,
    private val projectDir: File,
    private val buildDir: File,
    private val cacheDir: File,
    private val localRepoDir: File
) : BuildStep {

    override fun execute(callback: IBuildCallback?): BuildResult {
        callback?.onLog("[RN] Starting React Native Build...")

        // 1. Setup Android Shell
        val shellDir = File(buildDir, "rn_shell")
        if (shellDir.exists()) shellDir.deleteRecursively()
        shellDir.mkdirs()

        callback?.onLog("[RN] Copying Android shell...")
        copyAssetsRecursively(context, "templates/react_native/android", shellDir)

        // 2. Bundle JS
        val assetsDir = File(shellDir, "app/src/main/assets")
        assetsDir.mkdirs()

        callback?.onLog("[RN] Bundling JS...")
        val bundler = SimpleJsBundler()
        val bundleResult = bundler.bundle(projectDir, assetsDir)
        if (!bundleResult.success) {
            return bundleResult
        }
        callback?.onLog("[RN] JS Bundled: ${bundleResult.output}")

        // 3. Resolve Dependencies (React Native AARs)
        val dependenciesFile = File(shellDir, "dependencies.toml")
        // Write standard RN deps. Note: Versions must match what we expect.
        dependenciesFile.writeText("""
            "com.facebook.react:react-android" = "0.72.6"
            "com.facebook.react:hermes-android" = "0.72.6"
        """.trimIndent())

        val resolver = DependencyResolver(shellDir, dependenciesFile, localRepoDir)
        val resolverResult = resolver.execute(callback)
        if (!resolverResult.success) {
            return resolverResult
        }

        // 4. Run Android Build on Shell
        val aapt2Path = ToolManager.getToolPath(context, "aapt2")
        val kotlincJarPath = ToolManager.getToolPath(context, "kotlin-compiler.jar")
        val d8Path = ToolManager.getToolPath(context, "d8.jar")
        val apkSignerPath = ToolManager.getToolPath(context, "apksigner.jar")
        val keystorePath = ToolManager.getToolPath(context, "debug.keystore")
        val androidJarPath = ToolManager.getToolPath(context, "android.jar")
        val javaBinaryPath = ToolManager.getToolPath(context, "java")

        if (javaBinaryPath == null || aapt2Path == null || kotlincJarPath == null || d8Path == null || apkSignerPath == null || androidJarPath == null || keystorePath == null) {
             return BuildResult(false, "Missing build tools. Check logs.")
        }

        val MIN_SDK = 21
        val TARGET_SDK = 34

        val shellAppDir = File(shellDir, "app")
        val shellResDir = File(shellAppDir, "src/main/res")
        val shellManifest = File(shellAppDir, "src/main/AndroidManifest.xml")
        val shellJavaDir = File(shellAppDir, "src/main/java")
        val shellGenDir = File(buildDir, "gen")
        val shellClassesDir = File(buildDir, "classes")
        val compiledResDir = File(buildDir, "compiled_res")

        val steps = listOf(
            Aapt2Compile(aapt2Path, shellResDir.absolutePath, compiledResDir.absolutePath, MIN_SDK, TARGET_SDK),
            Aapt2Link(aapt2Path, compiledResDir.absolutePath, androidJarPath, shellManifest.absolutePath, File(buildDir, "app.apk").absolutePath, shellGenDir.absolutePath, MIN_SDK, TARGET_SDK),
            KotlincCompile(kotlincJarPath, androidJarPath, shellJavaDir.absolutePath, shellClassesDir, resolver.resolvedClasspath, javaBinaryPath),
            D8Compile(d8Path, javaBinaryPath, androidJarPath, shellClassesDir.absolutePath, shellClassesDir.absolutePath, resolver.resolvedClasspath),
            ApkBuild(File(buildDir, "app-signed.apk").absolutePath, File(buildDir, "app.apk").absolutePath, shellClassesDir.absolutePath),
            ApkSign(apkSignerPath, javaBinaryPath, keystorePath, "android", "androiddebugkey", File(buildDir, "app-signed.apk").absolutePath)
        )

        val orchestrator = BuildOrchestrator(steps)
        return orchestrator.execute(callback!!)
    }

    private fun copyAssetsRecursively(context: Context, path: String, dest: File) {
        val assets = try {
            context.assets.list(path)
        } catch (e: Exception) {
            null
        } ?: return

        if (assets.isEmpty()) {
            // It might be a file or empty dir. Try to open it as a file.
            copyAsset(context, path, dest)
        } else {
            // It's a directory (or a file that returns names? No, list on file returns nothing usually or null)
            // But list("") returns root assets.
            // Let's assume if list has items, it's a dir.
            if (!dest.exists()) dest.mkdirs()
            for (asset in assets) {
                val assetPath = if (path.isEmpty()) asset else "$path/$asset"
                val destFile = File(dest, asset)

                // Recursion check: is it a dir?
                val subAssets = try { context.assets.list(assetPath) } catch (e: Exception) { null }
                if (!subAssets.isNullOrEmpty()) {
                    copyAssetsRecursively(context, assetPath, destFile)
                } else {
                    // It's empty list. Could be file or empty dir.
                    // Try to open input stream.
                    var isFile = false
                    try {
                        context.assets.open(assetPath).close()
                        isFile = true
                    } catch (e: Exception) {
                        // Likely a directory
                    }

                    if (isFile) {
                        copyAsset(context, assetPath, destFile)
                    } else {
                         copyAssetsRecursively(context, assetPath, destFile)
                    }
                }
            }
        }
    }

    private fun copyAsset(context: Context, assetPath: String, destFile: File) {
         try {
            // Ensure parent exists
            destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            // Ignore if it's a directory we failed to identify
        }
    }
}
