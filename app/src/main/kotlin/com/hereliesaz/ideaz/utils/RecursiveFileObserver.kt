package com.hereliesaz.ideaz.utils

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.util.Stack

/**
 * A FileObserver that recursively watches a directory tree.
 * It manually creates a FileObserver for each subdirectory.
 */
class RecursiveFileObserver(
    private val rootPath: String,
    private val mask: Int = FileObserver.CLOSE_WRITE,
    private val onEvent: (Int, String?) -> Unit
) {

    private val observers = mutableListOf<FileObserver>()
    private val TAG = "RecursiveFileObserver"

    private val visited = mutableSetOf<String>()

    @Synchronized
    fun startWatching() {
        stopWatching() // Clean up existing observers
        visited.clear()

        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) return

        val stack = Stack<File>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            val dir = stack.pop()

            // Prevent infinite recursion (symlinks)
            val canonicalPath = dir.canonicalPath
            if (!visited.add(canonicalPath)) continue

            startWatchingDir(dir)

            dir.listFiles()?.forEach {
                if (it.isDirectory) {
                    stack.push(it)
                }
            }
        }
    }

    private fun handleEvent(event: Int, path: String?, dir: File) {
        val fullPath = if (path != null) File(dir, path).absolutePath else null

        // Handle new directories created
        if ((event and FileObserver.CREATE) != 0 || (event and FileObserver.MOVED_TO) != 0) {
                if (fullPath != null) {
                    val file = File(fullPath)
                    if (file.isDirectory) {
                        // Watch the new directory
                        startWatchingDir(file)
                    }
                }
        }

        // Forward event
        onEvent(event, fullPath)
    }

    private fun startWatchingDir(dir: File) {
         @Suppress("DEPRECATION")
         val observer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
             object : FileObserver(dir, mask or FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM) {
                    override fun onEvent(event: Int, path: String?) {
                         handleEvent(event, path, dir)
                    }
             }
         } else {
             object : FileObserver(dir.absolutePath, mask or FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM) {
                    override fun onEvent(event: Int, path: String?) {
                         handleEvent(event, path, dir)
                    }
             }
         }
         observer.startWatching()
         synchronized(observers) {
             observers.add(observer)
         }
    }

    @Synchronized
    fun stopWatching() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }
}
