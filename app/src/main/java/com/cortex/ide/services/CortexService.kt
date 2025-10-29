package com.cortex.ide.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import java.io.File
import java.io.IOException

class CortexService : Service() {

    // Define the remote repository URL and the local path
    private val remoteRepoUrl = "https://github.com/user/repo.git" // Placeholder
    private lateinit var localRepoPath: File

    override fun onCreate() {
        super.onCreate()
        localRepoPath = File(filesDir, "cortex-repo")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote the service to a foreground service
        val notification = NotificationCompat.Builder(this, "CORTEX_CHANNEL_ID")
            .setContentTitle("Cortex IDE")
            .setContentText("Cortex Service is running.")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder icon
            .build()

        startForeground(1, notification)

        // Trigger the git/compile loop in a background thread to avoid blocking the main thread
        Thread {
            pullLatestChanges()
            compileProject()
        }.start()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "CORTEX_CHANNEL_ID",
                "Cortex Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun pullLatestChanges() {
        try {
            if (localRepoPath.exists()) {
                // If the repository exists, pull the latest changes
                println("CortexService: Repository exists. Pulling latest changes...")
                val git = Git.open(localRepoPath)
                git.pull().call()
                git.close()
                println("CortexService: Pull complete.")
            } else {
                // If the repository does not exist, clone it
                println("CortexService: Repository does not exist. Cloning...")
                Git.cloneRepository()
                    .setURI(remoteRepoUrl)
                    .setDirectory(localRepoPath)
                    .call()
                println("CortexService: Clone complete.")
            }
        } catch (e: GitAPIException) {
            e.printStackTrace()
            println("CortexService: Git API Exception: ${e.message}")
        } catch (e: IOException) {
            e.printStackTrace()
            println("CortexService: IO Exception: ${e.message}")
        }
    }

    private fun compileProject() {
        if (!localRepoPath.exists()) {
            println("CortexService: Cannot compile, repository directory does not exist.")
            return
        }

        try {
            // Make the gradlew script executable
            val gradlew = File(localRepoPath, "gradlew")
            if (gradlew.exists()) {
                gradlew.setExecutable(true)
            } else {
                println("CortexService: gradlew script not found!")
                return
            }

            println("CortexService: Starting Gradle build...")
            val processBuilder = ProcessBuilder("./gradlew", "build")
            processBuilder.directory(localRepoPath) // Set the working directory
            processBuilder.redirectErrorStream(true) // Merge stdout and stderr

            val process = processBuilder.start()

            // Read the output from the process
            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                println("CortexService (Gradle): $line")
            }

            val exitCode = process.waitFor()
            println("CortexService: Gradle build finished with exit code: $exitCode")

        } catch (e: IOException) {
            e.printStackTrace()
            println("CortexService: IO Exception during compile: ${e.message}")
        } catch (e: InterruptedException) {
            e.printStackTrace()
            println("CortexService: Compile process was interrupted: ${e.message}")
        }
    }
}
