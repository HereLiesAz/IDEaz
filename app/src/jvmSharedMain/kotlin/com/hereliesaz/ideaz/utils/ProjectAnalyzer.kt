package com.hereliesaz.ideaz.utils

import java.io.File

/**
 * Answers the only question IDEaz needs to ask about a directory: can we mount
 * and preview it?
 *
 * This used to be `detectProjectType`, returning one of six `ProjectType`
 * values. Five of them were fiction. `ANDROID` drove a remote-APK build
 * pipeline and an on-device Wasm compiler that no longer exist; `WEB`,
 * `REACT`, `OTHER` and `UNKNOWN` were never selectable, and the analyzer never
 * returned `REACT` at all — every previewable project reported `PWA`
 * regardless of what it was. The taxonomy cost branching in a dozen files and
 * bought a label nobody could act on.
 */
object ProjectAnalyzer {

    /** Where a web entry point realistically lives, in priority order. */
    private val ENTRY_DIRS = listOf("", "public/", "src/", "app/", "www/", "docs/", "site/")

    /**
     * True when there is something the preview can mount.
     *
     * The question that matters is "is there a JS/HTML entry point we can
     * transpile in-browser?" — not "did someone remember to write a
     * webmanifest". The old rule required `index.html` at the repo root plus
     * one of four PWA marker files, which a standard React/Vite app does not
     * satisfy; IDEaz's own bundled React template failed it, so the 4.5 MB
     * React/Babel runtime shipped to preview exactly those projects was
     * unreachable for them.
     */
    fun isPreviewable(projectDir: File): Boolean {
        if (!projectDir.exists()) return false
        if (findWebEntryPoint(projectDir) != null) return true
        // A package.json is enough on its own: a Vite/Next project may keep
        // index.html somewhere this doesn't look, and the loader resolves the
        // real entry at mount time anyway.
        return File(projectDir, "package.json").isFile
    }

    /**
     * The project-relative path of the HTML entry point, or null.
     *
     * Checked against a short list of conventional locations rather than the repo
     * root alone, and never by walking the whole tree - on a phone, over a large
     * clone, that would be a visible stall on every project open.
     */
    fun findWebEntryPoint(projectDir: File): String? =
        ENTRY_DIRS.firstNotNullOfOrNull { dir ->
            "${dir}index.html".takeIf { File(projectDir, it).isFile }
        }
}
