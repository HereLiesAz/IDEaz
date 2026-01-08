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

    @Synchronized
    fun startWatching() {
        stopWatching() // Clean up existing observers

        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) return

        val stack = Stack<File>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            val dir = stack.pop()
            val observer = object : FileObserver(dir.absolutePath, mask or FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM) {
                override fun onEvent(event: Int, path: String?) {
                    val fullPath = if (path != null) File(dir, path).absolutePath else null

                    // Handle new directories created
                    if ((event and FileObserver.CREATE) != 0 || (event and FileObserver.MOVED_TO) != 0) {
                         if (fullPath != null) {
                             val file = File(fullPath)
                             if (file.isDirectory) {
                                 // Watch the new directory (and its children if any, though likely empty on create)
                                 startWatchingDir(file)
                             }
                         }
                    }

                    // Forward event
                    onEvent(event, if (path != null) File(dir, path).absolutePath else null)
                }
            }
            observer.startWatching()
            observers.add(observer)

            dir.listFiles()?.forEach {
                if (it.isDirectory) {
                    stack.push(it)
                }
            }
        }
    }

    private fun startWatchingDir(dir: File) {
         val observer = object : FileObserver(dir.absolutePath, mask or FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM) {
                override fun onEvent(event: Int, path: String?) {
                     // Recurse for nested creations
                     val fullPath = if (path != null) File(dir, path).absolutePath else null
                     if ((event and FileObserver.CREATE) != 0 || (event and FileObserver.MOVED_TO) != 0) {
                         if (fullPath != null) {
                             val file = File(fullPath)
                             if (file.isDirectory) {
                                 startWatchingDir(file)
                             }
                         }
                    }
                    onEvent(event, fullPath)
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
