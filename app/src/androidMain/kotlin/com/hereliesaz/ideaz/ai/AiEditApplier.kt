package com.hereliesaz.ideaz.ai

/**
 * Parses an AI's *text* reply into file edits and applies them with [IdeTools].
 *
 * Needed for backends that can't call tools (the Gemini app bridge): the model
 * is instructed to emit changes as fenced blocks, and this turns that text back
 * into real file writes / patches. Two block forms are recognised:
 *
 *   ```file:relative/path.ext
 *   <full new file contents>
 *   ```
 *
 *   ```diff
 *   <unified diff>
 *   ```
 *
 * Any other fenced block (plain ```kotlin etc.) is ignored, so explanatory code
 * samples in the reply are never mistaken for edits.
 */
object AiEditApplier {

    data class Edit(val path: String?, val isDiff: Boolean, val body: String)
    data class Outcome(val label: String, val ok: Boolean, val message: String)

    // Opening/closing code fence: 3+ backticks, capturing the info string.
    private val FENCE = Regex("""^\s*`{3,}\s*(.*?)\s*$""")

    fun parse(response: String): List<Edit> {
        val lines = response.split('\n')
        val edits = mutableListOf<Edit>()
        var i = 0
        while (i < lines.size) {
            val info = FENCE.matchEntire(lines[i])?.groupValues?.get(1)
            if (info == null) { i++; continue }

            val filePath = info.takeIf { it.startsWith("file:") }
                ?.removePrefix("file:")?.trim()?.trim('`', '"', '\'')
                ?.takeIf { it.isNotBlank() }
            val isDiff = info.equals("diff", ignoreCase = true)

            if (filePath == null && !isDiff) {
                // A non-edit fence (e.g. ```kotlin). Skip its whole block so its
                // contents aren't scanned for stray "file:" lines.
                i++
                while (i < lines.size && FENCE.matchEntire(lines[i]) == null) i++
                i++ // closing fence
                continue
            }

            // Collect the block body up to the closing fence.
            val body = StringBuilder()
            i++
            while (i < lines.size && FENCE.matchEntire(lines[i]) == null) {
                body.append(lines[i]).append('\n')
                i++
            }
            i++ // closing fence
            edits.add(Edit(path = filePath, isDiff = isDiff, body = body.toString()))
        }
        return edits
    }

    /** Parse [response] and apply every edit, returning a per-block outcome. */
    fun apply(response: String, tools: IdeTools): List<Outcome> =
        parse(response).map { e ->
            when {
                e.isDiff -> {
                    val r = tools.applyPatch(e.body)
                    Outcome("diff", r == "OK", r)
                }
                e.path != null -> {
                    val r = tools.writeFile(e.path, e.body.removeSuffix("\n"))
                    Outcome(e.path, r == "OK", r)
                }
                else -> Outcome("?", false, "Unrecognised edit block")
            }
        }
}
