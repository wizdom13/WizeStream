package org.schabi.newpipe.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R

class UpdateDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private data class DownloadedFile(
        val downloadedBytes: Long,
        val totalBytes: Long
    )

    override fun doWork(): Result {
        val apkUrl = inputData.getString(INPUT_APK_URL).orEmpty()
        val version = inputData.getString(INPUT_VERSION).orEmpty()
        val apkName = inputData.getString(INPUT_APK_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: defaultApkName(version)
        if (apkUrl.isBlank()) {
            return failure(applicationContext.getString(R.string.app_update_download_failed))
        }

        val updateDir = File(applicationContext.cacheDir, UPDATE_CACHE_DIR).apply { mkdirs() }
        val outputFile = File(updateDir, sanitizeFilename(apkName))
        return try {
            setForegroundAsync(createForegroundInfo(UNKNOWN_PROGRESS))
            val downloadedFile = download(apkUrl, outputFile)
            validateDownload(outputFile, downloadedFile)
            Result.success(
                workDataOf(
                    OUTPUT_APK_PATH to outputFile.absolutePath,
                    OUTPUT_APK_NAME to outputFile.name,
                    OUTPUT_VERSION to version
                )
            )
        } catch (e: Exception) {
            outputFile.delete()
            failure(e.message ?: applicationContext.getString(R.string.app_update_download_failed))
        }
    }

    private fun download(apkUrl: String, outputFile: File): DownloadedFile {
        val request = Request.Builder().url(apkUrl).build()
        DownloaderImpl.getInstance().client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response")
            val totalBytes = body.contentLength().takeIf { it >= 0 } ?: -1L
            var downloadedBytes = 0L
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastPercent = UNKNOWN_PROGRESS
                    while (true) {
                        if (isStopped) {
                            throw IOException("Download cancelled")
                        }
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        val percent = percent(downloadedBytes, totalBytes)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            publishProgress(percent, downloadedBytes, totalBytes)
                        }
                    }
                }
            }
            publishProgress(percent(downloadedBytes, totalBytes), downloadedBytes, totalBytes)
            return DownloadedFile(downloadedBytes, totalBytes)
        }
    }

    private fun validateDownload(outputFile: File, downloadedFile: DownloadedFile) {
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            throw IOException("Downloaded APK is empty")
        }
        if (outputFile.length() != downloadedFile.downloadedBytes) {
            throw IOException("Downloaded APK size mismatch")
        }
        if (downloadedFile.totalBytes >= 0L &&
            downloadedFile.downloadedBytes != downloadedFile.totalBytes
        ) {
            throw IOException("Downloaded APK is incomplete")
        }
    }

    private fun publishProgress(percent: Int, downloadedBytes: Long, totalBytes: Long) {
        setProgressAsync(
            workDataOf(
                PROGRESS_PERCENT to percent,
                PROGRESS_BYTES_DOWNLOADED to downloadedBytes,
                PROGRESS_TOTAL_BYTES to totalBytes
            )
        )
        setForegroundAsync(createForegroundInfo(percent))
    }

    private fun createForegroundInfo(percent: Int): ForegroundInfo {
        val channelId = applicationContext.getString(R.string.app_update_notification_channel_id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.wizestream_update_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(
                    R.string.wizestream_update_notification_channel_description
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
        val progressText = if (percent < 0) {
            applicationContext.getString(R.string.app_update_download_progress_unknown)
        } else {
            applicationContext.getString(R.string.app_update_download_progress_message, percent)
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_wizestream_update)
            .setContentTitle(
                applicationContext.getString(R.string.app_update_download_progress_title)
            )
            .setContentText(progressText)
            .setOngoing(true)
            .setProgress(100, percent.coerceIn(0, 100), percent < 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun failure(error: String): Result {
        return Result.failure(workDataOf(OUTPUT_ERROR to error))
    }

    companion object {
        private const val UPDATE_CACHE_DIR = "update"
        private const val NOTIFICATION_ID = 2001
        private const val UNKNOWN_PROGRESS = -1
        const val INPUT_APK_URL = "apkUrl"
        const val INPUT_APK_NAME = "apkName"
        const val INPUT_VERSION = "version"
        const val PROGRESS_PERCENT = "progressPercent"
        const val PROGRESS_BYTES_DOWNLOADED = "progressBytesDownloaded"
        const val PROGRESS_TOTAL_BYTES = "progressTotalBytes"
        const val OUTPUT_APK_PATH = "apkPath"
        const val OUTPUT_APK_NAME = "apkName"
        const val OUTPUT_VERSION = "version"
        const val OUTPUT_ERROR = "error"

        @JvmStatic
        fun enqueue(context: Context, apkUrl: String, apkName: String?, version: String): UUID {
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_APK_URL to apkUrl,
                        INPUT_APK_NAME to apkName.orEmpty(),
                        INPUT_VERSION to version
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
            return request.id
        }

        private fun defaultApkName(version: String): String {
            return "WizeStream_${version.ifBlank { "update" }}.apk"
        }

        private fun sanitizeFilename(filename: String): String {
            val sanitized = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return sanitized.lowercase(Locale.ROOT).takeIf { it.endsWith(".apk") }
                ?: "$sanitized.apk"
        }

        private fun percent(downloadedBytes: Long, totalBytes: Long): Int {
            if (totalBytes <= 0L) {
                return -1
            }
            return ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        }
    }
}
