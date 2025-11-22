package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.models.SourceMapEntry
import kotlinx.serialization.json.Json
import java.io.File

class SourceMapParser(private val buildDir: File) {

    fun parse(): Map<String, SourceMapEntry> {
        val sourceMapFile = File(buildDir, "source_map.json")
        if (!sourceMapFile.exists()) {
            return emptyMap()
        }

        val json = sourceMapFile.readText()
        val entries = Json.decodeFromString<List<SourceMapEntry>>(json)
        return entries.associateBy { it.id }
    }
}

