package com.hereliesaz.ideaz.buildlogic

import java.io.File

data class SourceMapEntry(val id: String, val file: String, val line: Int)

class GenerateSourceMap(
    private val resDir: String,
    private val buildDir: String
) : BuildStep {

    override fun execute(): BuildResult {
        try {
            val sourceMap = mutableListOf<SourceMapEntry>()
            val layoutDir = File(resDir, "layout")
            val idRegex = Regex("""android:id="@+id/(\w+)"""")

            if (layoutDir.exists()) {
                layoutDir.listFiles()?.forEach { file ->
                    file.readLines().forEachIndexed { lineNumber, line ->
                        idRegex.find(line)?.let {
                            val id = it.groupValues[1]
                            sourceMap.add(SourceMapEntry(id, file.absolutePath, lineNumber + 1))
                        }
                    }
                }
            }

            val sourceMapFile = File(buildDir, "source_map.json")
            sourceMapFile.writeText(sourceMap.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]") {
                "  { \"id\": \"${it.id}\", \"file\": \"${it.file}\", \"line\": ${it.line} }"
            })

            return BuildResult(true, "Source map generated successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            return BuildResult(false, e.message ?: "Unknown error")
        }
    }
}
