package com.hereliesaz.ideaz.utils

import android.os.FileObserver
import java.io.File

/**
 * Watches a project directory - and, unlike a bare [FileObserver], every
 * subdirectory in it - for changes, to trigger a soft WebView reload.
 *
 * [FileObserver]'s single-[File] constructor only watches the directory it's
 * given, not recursively. Since virtually every real project keeps its
 * actual source under a subdirectory (`src/`, `public/`, ...), that meant
 * edits to the files that matter - proven by execution: watch a project
 * root, edit `src/App.jsx`, get zero events - never triggered a reload at
 * all. The multi-path constructor (API 29+; this app's minSdk is 30) is
 * what recursive watching actually requires.
 */
class ProjectFileObserver(
    path: String,
    private val onChange: () -> Unit
) : FileObserver(watchedDirectories(File(path)), CLOSE_WRITE or MOVED_FROM or MOVED_TO or CREATE or DELETE) {

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return
        onChange()
    }

    companion object {
        // Same noise directories IdeTools.repoMap skips - watching
        // node_modules alone can mean tens of thousands of directories,
        // which is both wasteful and a real inotify watch-count concern.
        private val SKIP_DIRS = setOf(
            ".git", "node_modules", "build", ".gradle", ".idea",
            "dist", ".next", "out", ".cache", "__ideaz__"
        )

        // A directory created *after* watching starts (e.g. the AI's first
        // write into a brand-new src/components/ folder) isn't picked up
        // until the next startFileObservation() call - the same limitation
        // any snapshot-based recursive watch has without also watching for
        // CREATE events on directories and dynamically adding new watches.
        // A soft reload already happens on the next file event anywhere
        // else in the project, and every AI-driven edit gets its own
        // explicit reload via the edit-review flow regardless of this
        // watcher, so the gap is real but narrow.
        private fun watchedDirectories(root: File): List<File> {
            val dirs = mutableListOf(root)
            fun walk(dir: File) {
                dir.listFiles()?.forEach { child ->
                    if (child.isDirectory && child.name !in SKIP_DIRS) {
                        dirs.add(child)
                        walk(child)
                    }
                }
            }
            walk(root)
            return dirs
        }
    }
}
