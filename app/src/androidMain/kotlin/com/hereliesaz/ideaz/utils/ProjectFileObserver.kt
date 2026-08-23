package com.hereliesaz.ideaz.utils

import android.os.FileObserver
import java.io.File

class ProjectFileObserver(
    path: String,
    private val onChange: () -> Unit
) : FileObserver(File(path), CLOSE_WRITE or MOVED_FROM or MOVED_TO or CREATE or DELETE) {

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return
        onChange()
    }
}
