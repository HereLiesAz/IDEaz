package com.hereliesaz.ideaz.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.hereliesaz.ideaz.models.ACTION_WASM_COMPILE_SUCCESS
import com.hereliesaz.ideaz.models.EXTRA_WWW_DIR
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * On-device Kotlin/Wasm compiler for **Android** (Compose Multiplatform)
 * projects. Replaces the old Zipline-era `JsCompilerService`: instead of
 * bundling JS for a guest runtime, it drives the embedded Kotlin compiler with
 * its WebAssembly backend and drops the result straight into `filesDir/www/`,
 * where [com.hereliesaz.ideaz.ui.web.WebProjectHost] mounts it.
 *
 * Only the local preview — "the mirage" — is produced here. The real APK is
 * assembled remotely by the template's `.github/workflows/build-apk.yml`.
 *
 * Inputs are the project's `commonMain` and `wasmJsMain` source sets; outputs
 * are `<module>.wasm` (the binary), `<module>.js` (the loader script) and
 * `index.html` (the canvas host). Compilation is a pure function of those
 * sources — nothing else in the project tree is read.
 *
 * The compiler itself is not linked into IDEaz. It is loaded at runtime, by
 * reflection, from the dexed `kotlin-compiler-embeddable` staged out of
 * `assets/[COMPILER_ASSET_DIR]`, so a build without those assets degrades to a
 * clean error instead of failing to link.
 */
class WasmCompilerService(private val context: Context) {

    /**
     * @param success   True when the compiler exited OK and a `.wasm` binary
     *                  plus a loader script landed in [wwwDir].
     * @param log       Full compiler output, ready for the build console.
     * @param wwwDir    `filesDir/www` — the directory the WebView mounts.
     */
    data class Result(val success: Boolean, val log: String, val wwwDir: File)

    /**
     * Compiles [projectDir]'s common + wasmJs sources into `filesDir/www/`.
     *
     * On success a [ACTION_WASM_COMPILE_SUCCESS] broadcast is sent so the
     * preview WebView hot-reloads the new binary.
     *
     * @param projectDir Root of the user's Android (KMP) project.
     * @param moduleName Base name for the emitted `.wasm` / `.js` pair.
     */
    suspend fun compile(
        projectDir: File,
        moduleName: String = DEFAULT_MODULE_NAME,
    ): Result = withContext(Dispatchers.IO) {
        val wwwDir = wwwDir()
        val log = StringBuilder()

        val sources = collectSources(projectDir)
        if (sources.isEmpty()) {
            return@withContext Result(
                false,
                "[WASM] No sources found. Expected Kotlin files under " +
                    "app/src/commonMain/kotlin and app/src/wasmJsMain/kotlin.\n",
                wwwDir,
            )
        }
        log.append("[WASM] Compiling ${sources.size} file(s) to WebAssembly...\n")

        val compiler = loadCompiler()
        if (compiler == null) {
            return@withContext Result(
                false,
                log.append(
                    "[WASM] Embedded Kotlin compiler unavailable: no dex archives in " +
                        "assets/$COMPILER_ASSET_DIR/.\n",
                ).toString(),
                wwwDir,
            )
        }

        // Wipe only the artifacts we own; user-authored files in www/ survive.
        wwwDir.mkdirs()
        listOf("$moduleName.wasm", "$moduleName.js", "$moduleName.mjs").forEach {
            File(wwwDir, it).delete()
        }

        val args = compilerArgs(sources, stageKlibs(projectDir), wwwDir, moduleName)
        log.append("[WASM] kotlinc ${args.joinToString(" ")}\n")

        val (exitOk, compilerOutput) = invoke(compiler, args)
        log.append(compilerOutput)
        if (!exitOk) {
            return@withContext Result(false, log.append("[WASM] Compilation failed.\n").toString(), wwwDir)
        }

        val binary = File(wwwDir, "$moduleName.wasm")
        if (!binary.isFile) {
            return@withContext Result(
                false,
                log.append("[WASM] Compiler exited OK but produced no $moduleName.wasm.\n").toString(),
                wwwDir,
            )
        }

        val loader = ensureLoaderScript(wwwDir, moduleName)
        if (loader == null) {
            return@withContext Result(
                false,
                log.append("[WASM] No loader script emitted next to $moduleName.wasm.\n").toString(),
                wwwDir,
            )
        }

        writeHostPage(wwwDir, loader.name)
        log.append("[WASM] ${binary.name} (${binary.length()} bytes) + ${loader.name} -> ${wwwDir.absolutePath}\n")

        context.sendBroadcast(
            Intent(ACTION_WASM_COMPILE_SUCCESS).apply {
                putExtra(EXTRA_WWW_DIR, wwwDir.absolutePath)
                setPackage(context.packageName)
            },
        )

        Result(true, log.toString(), wwwDir)
    }

    /** `filesDir/www` — the single mount point for compiled previews. */
    fun wwwDir(): File = File(context.filesDir, WWW_DIR_NAME).apply { mkdirs() }

    // --- Sources ------------------------------------------------------------

    /**
     * All `.kt` files under the project's `commonMain` and `wasmJsMain` source
     * sets. The `app/src/…` layout of the bundled template is preferred; a
     * root-level `src/…` layout is accepted as a fallback.
     */
    private fun collectSources(projectDir: File): List<String> =
        SOURCE_SET_ROOTS
            .map { File(projectDir, it) }
            .filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .map { it.absolutePath }
            .distinct()

    // --- Compiler plumbing --------------------------------------------------

    /**
     * Kotlin/Wasm CLI invocation. `-Xwasm` selects the WebAssembly backend and
     * `-Xwasm-target=wasm-js` the browser (JS-interop) flavour; `-Xir-produce-js`
     * makes the backend emit the loader script beside the binary.
     */
    private fun compilerArgs(
        sources: List<String>,
        klibs: List<File>,
        outDir: File,
        moduleName: String,
    ): List<String> = buildList {
        addAll(sources)
        add("-Xwasm")
        add("-Xwasm-target=wasm-js")
        add("-Xir-produce-js")
        add("-Xir-module-name=$moduleName")
        add("-ir-output-dir")
        add(outDir.absolutePath)
        add("-ir-output-name")
        add(moduleName)
        if (klibs.isNotEmpty()) {
            add("-libraries")
            add(klibs.joinToString(File.pathSeparator) { it.absolutePath })
        }
    }

    /**
     * Klib dependencies handed to the compiler: the bundled Kotlin/Wasm stdlib
     * and Compose Multiplatform klibs from `assets/[KLIB_ASSET_DIR]`, plus any
     * the project ships in `libs/wasm/`.
     */
    private fun stageKlibs(projectDir: File): List<File> {
        val staged = stageAssets(KLIB_ASSET_DIR, File(context.filesDir, KLIB_ASSET_DIR)) { it.endsWith(".klib") }
        val projectKlibs = File(projectDir, PROJECT_KLIB_DIR)
            .listFiles { f -> f.isFile && f.name.endsWith(".klib") }
            ?.toList()
            .orEmpty()
        return staged + projectKlibs
    }

    /**
     * Loads the embedded compiler's entry class out of the dexed archives in
     * `assets/[COMPILER_ASSET_DIR]`. Returns `null` when nothing is bundled.
     */
    private fun loadCompiler(): Class<*>? {
        val jars = stageAssets(COMPILER_ASSET_DIR, File(context.filesDir, COMPILER_ASSET_DIR)) {
            it.endsWith(".jar") || it.endsWith(".dex")
        }
        if (jars.isEmpty()) return null

        return try {
            val optimizedDir = File(context.codeCacheDir, COMPILER_ASSET_DIR).apply { mkdirs() }
            DexClassLoader(
                jars.joinToString(File.pathSeparator) { it.absolutePath },
                optimizedDir.absolutePath,
                null,
                javaClass.classLoader,
            ).loadClass(COMPILER_ENTRY_CLASS)
        } catch (e: Throwable) {
            Log.w(TAG, "Could not load $COMPILER_ENTRY_CLASS", e)
            null
        }
    }

    /**
     * Runs `CLICompiler.exec(PrintStream, String...)` reflectively and captures
     * its diagnostics. Returns exit-OK plus the captured output.
     */
    private fun invoke(compiler: Class<*>, args: List<String>): Pair<Boolean, String> {
        val buffer = ByteArrayOutputStream()
        return try {
            val instance = compiler.getDeclaredConstructor().newInstance()
            val exec = compiler.getMethod("exec", PrintStream::class.java, Array<String>::class.java)
            val exitCode = PrintStream(buffer, true, Charsets.UTF_8.name()).use { stream ->
                exec.invoke(instance, stream, args.toTypedArray())
            }
            val ok = (exitCode as? Enum<*>)?.name == EXIT_CODE_OK
            ok to buffer.toString(Charsets.UTF_8.name())
        } catch (e: Throwable) {
            Log.w(TAG, "Wasm compilation threw", e)
            false to (buffer.toString(Charsets.UTF_8.name()) + "[WASM] ${e.message ?: e.javaClass.simpleName}\n")
        }
    }

    // --- Output shaping -----------------------------------------------------

    /**
     * Guarantees a `<module>.js` loader exists beside the binary. The Wasm
     * backend emits an ES module (`.mjs`); when that is what landed, a one-line
     * `.js` re-export is written so the host page always has a stable entry
     * point to import.
     */
    private fun ensureLoaderScript(wwwDir: File, moduleName: String): File? {
        val js = File(wwwDir, "$moduleName.js")
        if (js.isFile) return js

        val emitted = wwwDir.listFiles()
            ?.firstOrNull { it.name == "$moduleName.mjs" }
            ?: wwwDir.listFiles()?.firstOrNull {
                it.isFile && it.extension == "mjs" && !it.name.endsWith(UNINSTANTIATED_SUFFIX)
            }
            ?: return null

        js.writeText("import './${emitted.name}';\n")
        return js
    }

    /**
     * Writes the canvas host page. The loader is pulled in with a dynamic
     * `import()` from a classic script deliberately: a `<script type="module">`
     * would be rewritten by the preview host's JSX runtime injection and never
     * execute.
     */
    private fun writeHostPage(wwwDir: File, loaderName: String) {
        File(wwwDir, "index.html").writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                <title>IDEaz Preview</title>
                <style>html,body{margin:0;padding:0;height:100%;overflow:hidden;background:#fff}</style>
            </head>
            <body>
            <script>
                import('./$loaderName').catch(function (e) {
                    document.body.textContent = 'Wasm load failed: ' + e;
                });
            </script>
            </body>
            </html>
            """.trimIndent() + "\n",
        )
    }

    // --- Asset staging ------------------------------------------------------

    /**
     * Copies `assets/[assetDir]` entries matching [accept] into [destDir],
     * skipping files already staged at the same size. Returns the staged files.
     */
    private fun stageAssets(assetDir: String, destDir: File, accept: (String) -> Boolean): List<File> {
        val names = try {
            context.assets.list(assetDir)?.filter(accept).orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not list assets/$assetDir", e)
            return emptyList()
        }
        if (names.isEmpty()) return emptyList()

        destDir.mkdirs()
        return names.mapNotNull { name ->
            val dest = File(destDir, name)
            try {
                context.assets.open("$assetDir/$name").use { input ->
                    val bytes = input.readBytes()
                    if (!dest.isFile || dest.length() != bytes.size.toLong()) {
                        dest.writeBytes(bytes)
                    }
                }
                dest
            } catch (e: Exception) {
                Log.w(TAG, "Could not stage $assetDir/$name", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "WasmCompilerService"

        /** Output directory, served at the WebView asset-loader origin root. */
        const val WWW_DIR_NAME = "www"

        /** Default base name of the emitted binary and loader. */
        const val DEFAULT_MODULE_NAME = "app"

        /** Source sets fed to the compiler, in the order they are resolved. */
        private val SOURCE_SET_ROOTS = listOf(
            "app/src/commonMain/kotlin",
            "app/src/wasmJsMain/kotlin",
            "src/commonMain/kotlin",
            "src/wasmJsMain/kotlin",
        )

        private const val COMPILER_ASSET_DIR = "wasm-compiler"
        private const val KLIB_ASSET_DIR = "wasm-klibs"
        private const val PROJECT_KLIB_DIR = "libs/wasm"
        private const val COMPILER_ENTRY_CLASS = "org.jetbrains.kotlin.cli.js.K2JSCompiler"
        private const val EXIT_CODE_OK = "OK"
        private const val UNINSTANTIATED_SUFFIX = ".uninstantiated.mjs"
    }
}
