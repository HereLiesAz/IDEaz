package com.hereliesaz.ideaz.ai.local

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hereliesaz.ideaz.ui.SettingsViewModel
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Classifies failures that can plausibly succeed after WorkManager backoff. */
internal fun isRetryableModelDownloadFailure(message: String): Boolean =
    !message.startsWith("Not enough storage") &&
        !message.startsWith("Model catalog") &&
        !message.contains("mismatch") &&
        !message.startsWith("Invalid ") &&
        !Regex("HTTP 4\\d\\d").containsMatchIn(message)

/**
 * Durable, unique download job for one catalog model.
 *
 * Only the catalog ID enters WorkManager's database. Authentication is resolved
 * inside the worker so provider tokens are never serialized into WorkRequest input,
 * progress, output, logs, or notifications. WorkManager supplies network gating,
 * persisted progress/state, cancellation, retry/backoff, and process restoration.
 */
class LocalModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return failure("Missing model ID")
        val model = LocalModelCatalog.byId(modelId)
            ?: return failure("Model '$modelId' is no longer in the catalog")
        if (model.systemManaged) return failure("System-managed models are not downloadable")

        setForeground(foregroundInfo(model.name, downloaded = 0L, total = model.approxSizeBytes))
        val settings = SettingsViewModel(applicationContext as Application)
        val token = settings.getApiKey(SettingsViewModel.KEY_HF_API_KEY)?.takeIf { it.isNotBlank() }

        return try {
            var lastReportedBytes = -1L
            var lastReportedAt = 0L
            ModelDownloadManager(applicationContext).download(model, token) { downloaded, total ->
                val now = SystemClock.elapsedRealtime()
                val complete = total > 0L && downloaded >= total
                if (!complete &&
                    downloaded - lastReportedBytes < MIN_PROGRESS_STEP_BYTES &&
                    now - lastReportedAt < MIN_PROGRESS_INTERVAL_MS
                ) {
                    return@download
                }
                lastReportedBytes = downloaded
                lastReportedAt = now
                setProgressAsync(
                    workDataOf(
                        KEY_DOWNLOADED_BYTES to downloaded,
                        KEY_TOTAL_BYTES to total,
                    )
                )
                setForegroundAsync(foregroundInfo(model.name, downloaded, total))
            }
            Result.success(workDataOf(KEY_MODEL_ID to modelId))
        } catch (e: IOException) {
            if (runAttemptCount < MAX_RETRIES && isRetryableModelDownloadFailure(e.message.orEmpty())) {
                Result.retry()
            } else {
                failure(e.message ?: "Download failed")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e.message ?: "Download failed")
        }
    }

    private fun failure(message: String): Result =
        Result.failure(workDataOf(KEY_ERROR to message.take(MAX_ERROR_LENGTH)))

    private fun foregroundInfo(modelName: String, downloaded: Long, total: Long): ForegroundInfo {
        createNotificationChannel()
        val determinate = total > 0L
        val percent = if (determinate) {
            ((downloaded.coerceAtLeast(0L).coerceAtMost(total) * 100L) / total).toInt()
        } else {
            0
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading on-device model")
            .setContentText(if (determinate) "$modelName · $percent%" else modelName)
            .setProgress(100, percent, !determinate)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            )
            .build()
        return ForegroundInfo(
            notificationId(inputData.getString(KEY_MODEL_ID).orEmpty()),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun createNotificationChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "On-device model downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "ideaz_local_model_downloads"
        private const val UNIQUE_WORK_PREFIX = "local-model-download:"
        private const val MAX_RETRIES = 3
        private const val MAX_ERROR_LENGTH = 1_024
        private const val MIN_PROGRESS_STEP_BYTES = 4L * 1024L * 1024L
        private const val MIN_PROGRESS_INTERVAL_MS = 1_000L

        fun uniqueWorkName(modelId: String): String = UNIQUE_WORK_PREFIX + modelId

        /** Enqueues at most one active job for [modelId]. */
        fun enqueue(context: Context, modelId: String) {
            val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
                .setInputData(workDataOf(KEY_MODEL_ID to modelId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .addTag(uniqueWorkName(modelId))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueWorkName(modelId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, modelId: String) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(uniqueWorkName(modelId))
        }

        private fun notificationId(modelId: String): Int =
            20_000 + (modelId.hashCode() and 0x0FFF)
    }
}
