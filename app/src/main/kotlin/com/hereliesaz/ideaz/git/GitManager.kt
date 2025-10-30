package com.hereliesaz.ideaz.git

import org.eclipse.jgit.api.Git
import java.io.File
import java.io.ByteArrayInputStream

class GitManager(private val projectDir: File) {

    fun init() {
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        Git.init().setDirectory(projectDir).call()
    }

    fun applyPatch(patch: String) {
        val git = Git.open(projectDir)
        val patchInputStream = ByteArrayInputStream(patch.toByteArray())
        git.apply().setPatch(patchInputStream).call()
    }
}
