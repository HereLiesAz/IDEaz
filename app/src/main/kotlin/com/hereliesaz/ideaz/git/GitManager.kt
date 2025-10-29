package com.hereliesaz.ideaz.git

import org.eclipse.jgit.api.Git
import java.io.File

class GitManager(private val projectDir: File) {

    fun init() {
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        Git.init().setDirectory(projectDir).call()
    }
}
