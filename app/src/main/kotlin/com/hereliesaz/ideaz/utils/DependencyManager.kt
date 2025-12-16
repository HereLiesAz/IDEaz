package com.hereliesaz.ideaz.utils

import java.io.File

data class DependencyItem(
    val alias: String,
    val group: String,
    val name: String,
    val version: String
)

object DependencyManager {

    fun listDependencies(projectDir: File): List<DependencyItem> {
        val tomlFile = File(projectDir, "gradle/libs.versions.toml")
        if (!tomlFile.exists()) return emptyList()

        val content = tomlFile.readText()
        val versions = mutableMapOf<String, String>()
        val dependencies = mutableListOf<DependencyItem>()

        try {
            // 1. Parse Versions
            val versionsSection = content.substringAfter("[versions]", "").substringBefore("[libraries]")
            versionsSection.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    val parts = trimmed.split("=")
                    if (parts.size >= 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim().replace("\"", "").replace("'", "")
                        versions[key] = value
                    }
                }
            }

            // 2. Parse Libraries
            val librariesSection = content.substringAfter("[libraries]", "").substringBefore("[plugins]")
            librariesSection.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size >= 2) {
                        val alias = parts[0].trim()
                        val value = parts[1].trim()

                        if (value.startsWith("{")) {
                            // { module = "group:name", version.ref = "ver" }
                            val moduleMatch = Regex("""module\s*=\s*["']([^"']+)["']""").find(value)
                            val versionRefMatch = Regex("""version\.ref\s*=\s*["']([^"']+)["']""").find(value)
                            val versionMatch = Regex("""version\s*=\s*["']([^"']+)["']""").find(value)

                            if (moduleMatch != null) {
                                val module = moduleMatch.groupValues[1]
                                val (group, name) = if (module.contains(":")) {
                                    val split = module.split(":")
                                    split[0] to split.getOrElse(1) { "" }
                                } else {
                                    "" to ""
                                }

                                var version = "?"
                                if (versionRefMatch != null) {
                                    val ref = versionRefMatch.groupValues[1]
                                    version = versions[ref] ?: "ref:$ref"
                                } else if (versionMatch != null) {
                                    version = versionMatch.groupValues[1]
                                }

                                dependencies.add(DependencyItem(alias, group, name, version))
                            } else {
                                // Maybe group/name format?
                                val groupMatch = Regex("""group\s*=\s*["']([^"']+)["']""").find(value)
                                val nameMatch = Regex("""name\s*=\s*["']([^"']+)["']""").find(value)
                                if (groupMatch != null && nameMatch != null) {
                                    val group = groupMatch.groupValues[1]
                                    val name = nameMatch.groupValues[1]

                                    var version = "?"
                                    if (versionRefMatch != null) {
                                        val ref = versionRefMatch.groupValues[1]
                                        version = versions[ref] ?: "ref:$ref"
                                    } else if (versionMatch != null) {
                                        version = versionMatch.groupValues[1]
                                    }
                                    dependencies.add(DependencyItem(alias, group, name, version))
                                }
                            }

                        } else {
                            // alias = "group:name:version"
                            val cleanValue = value.replace("\"", "").replace("'", "")
                            val depParts = cleanValue.split(":")
                            if (depParts.size >= 3) {
                                dependencies.add(DependencyItem(alias, depParts[0], depParts[1], depParts[2]))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return dependencies
    }
}
