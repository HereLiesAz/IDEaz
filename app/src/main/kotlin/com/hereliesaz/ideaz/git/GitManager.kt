package com.hereliesaz.ideaz.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File

class GitManager(private val projectDir: File) {

    private val git: Git by lazy {
        Git.open(projectDir)
    }

    suspend fun commit(message: String) {
        withContext(Dispatchers.IO) {
            git.commit().setMessage(message).call()
        }
    }
}
