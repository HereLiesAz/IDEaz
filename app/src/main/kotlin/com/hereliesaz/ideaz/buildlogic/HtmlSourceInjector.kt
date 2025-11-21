package com.hereliesaz.ideaz.buildlogic

class HtmlSourceInjector {

    fun inject(lines: List<String>, fileName: String): String {
        return lines.mapIndexed { index, line ->
            processLine(line, fileName, index + 1)
        }.joinToString("\n")
    }

    private fun processLine(line: String, fileName: String, lineNumber: Int): String {
        // Regex to match opening tags.
        // <([a-zA-Z0-9-]+)  Matches <div, <custom-element
        // ([^>]*)           Matches attributes
        // >                 Matches closing bracket
        // We exclude tags starting with / (closing tags) or ! (comments/doctype)
        val regex = Regex("<([a-zA-Z0-9-]+)([^>]*)>")

        return regex.replace(line) { match ->
            val tagName = match.groupValues[1]
            val attributes = match.groupValues[2]

            // Skip if already has aria-label to avoid overwrite/duplication?
            // For "post-code" context, we MIGHT want to overwrite or append.
            // But let's assume if it exists, we skip to be safe, or append?
            // Appending is safer for functionality but harder to parse back.
            // Let's skip if exists for MVP.

            if (attributes.contains("aria-label")) {
                match.value
            } else {
                // Check if self-closing slash is at the end of attributes
                if (attributes.trimEnd().endsWith("/")) {
                    val slashIndex = attributes.lastIndexOf('/')
                    val beforeSlash = attributes.substring(0, slashIndex)
                    "<$tagName$beforeSlash aria-label=\"__source:$fileName:${lineNumber}__\" />"
                } else {
                    "<$tagName$attributes aria-label=\"__source:$fileName:${lineNumber}__\">"
                }
            }
        }
    }
}
