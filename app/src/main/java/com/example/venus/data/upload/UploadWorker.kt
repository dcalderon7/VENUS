package com.example.venus.data.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.venus.network.VenusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_URI = "key_uri"
        const val KEY_TMDB_ID = "key_tmdb_id"
        const val KEY_MEDIA_TYPE = "key_media_type"
        const val KEY_DISPLAY_TITLE = "key_display_title"
        const val KEY_PROGRESS = "key_progress"
        const val KEY_RESULT_MESSAGE = "key_result_message"

        const val WORK_NAME_PREFIX = "upload_work_"
        private const val NOTIFICATION_CHANNEL_ID = "upload_channel"
    }

    private val notificationId = id.hashCode()
    private val repository = VenusRepository(applicationContext)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(inputData.getInt(KEY_PROGRESS, 0))
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Subidas de Video",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el progreso de subida de videos al servidor VENUS"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Subiendo video a VENUS")
            .setContentText("Progreso: $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workerId = id.toString()
        val uriString = inputData.getString(KEY_URI) ?: return@withContext Result.failure(
            workDataOf(KEY_RESULT_MESSAGE to "URI de video no proporcionado")
        )
        val tmdbId = inputData.getString(KEY_TMDB_ID) ?: ""
        val mediaType = inputData.getString(KEY_MEDIA_TYPE) ?: "movie"
        val displayTitle = inputData.getString(KEY_DISPLAY_TITLE) ?: ""

        val uri = Uri.parse(uriString)

        try {
            applicationContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        try {
            try {
                setForeground(getForegroundInfo())
            } catch (_: Exception) {}

            val uploadResult = repository.uploadMedia(uri, tmdbId, mediaType, displayTitle, workerId) { percent ->
                val progressData = workDataOf(KEY_PROGRESS to percent)
                setProgressAsync(progressData)
                try {
                    setForegroundAsync(createForegroundInfo(percent))
                } catch (_: Exception) {}
            }

            if (isStopped) {
                repository.cancelUpload(workerId)
                return@withContext Result.failure(
                    workDataOf(KEY_RESULT_MESSAGE to "Subida cancelada")
                )
            }

            if (uploadResult.isSuccess) {
                val response = uploadResult.getOrNull()
                Result.success(
                    workDataOf(KEY_RESULT_MESSAGE to (response?.message ?: "Subida exitosa"))
                )
            } else {
                val errorMsg = uploadResult.exceptionOrNull()?.message ?: "Error desconocido en la subida"
                Result.failure(
                    workDataOf(KEY_RESULT_MESSAGE to errorMsg)
                )
            }
        } catch (e: Exception) {
            if (isStopped) {
                repository.cancelUpload(workerId)
            }
            Result.failure(
                workDataOf(KEY_RESULT_MESSAGE to (e.message ?: "Excepción en UploadWorker"))
            )
        }
    }
}
