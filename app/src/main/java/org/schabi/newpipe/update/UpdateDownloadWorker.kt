package org.schabi.newpipe.update

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
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
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R

class UpdateDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val download = UpdateApkDownload { request ->
        DownloaderImpl.getInstance().client.newCall(request)
    }
    private val completionLock = Any()

    override fun onStopped() {
        download.cancel()
        synchronized(completionLock) {
            UpdateDownloadNotifications.dismissCompleted(applicationContext, id)
        }
        super.onStopped()
    }

    override fun doWork(): Result {
        val apkUrl = inputData.getString(INPUT_APK_URL).orEmpty()
        val version = inputData.getString(INPUT_VERSION).orEmpty()
        val apkName = inputData.getString(INPUT_APK_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: defaultApkName(version)
        val expectedSize = inputData.getLong(INPUT_APK_SIZE, -1L)
        val expectedSha256 = UpdateChecksum.normalizeSha256(
            inputData.getString(INPUT_APK_SHA256)
        )
        if (apkUrl.isBlank() || version.isBlank() || expectedSize <= 0L ||
            expectedSha256 == null
        ) {
            return failure(applicationContext.getString(R.string.app_update_download_failed))
        }

        val updateDir = File(applicationContext.cacheDir, UpdateCacheCleanup.DIRECTORY)
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            return failure(applicationContext.getString(R.string.app_update_download_failed))
        }
        val outputFile = File(updateDir, "$id-${sanitizeFilename(apkName)}")
        return try {
            setForegroundAsync(createForegroundInfo(UNKNOWN_PROGRESS))
            checkStopped()
            var lastPercent = Int.MIN_VALUE
            download.download(apkUrl, outputFile, expectedSize) { downloadedBytes, totalBytes ->
                val percent = percent(downloadedBytes, totalBytes)
                if (percent != lastPercent) {
                    lastPercent = percent
                    publishProgress(percent, downloadedBytes, totalBytes)
                }
            }
            checkStopped()
            UpdateApkVerifier.verify(
                applicationContext,
                outputFile,
                expectedSha256,
                version
            )
            synchronized(completionLock) {
                checkStopped()
                UpdateDownloadNotifications.completed(applicationContext, id, version)
            }
            Result.success(
                workDataOf(
                    OUTPUT_APK_PATH to outputFile.absolutePath,
                    OUTPUT_APK_NAME to outputFile.name,
                    OUTPUT_VERSION to version,
                    OUTPUT_APK_SHA256 to expectedSha256
                )
            )
        } catch (e: Exception) {
            outputFile.delete()
            failure(e.message ?: applicationContext.getString(R.string.app_update_download_failed))
        }
    }

    private fun checkStopped() {
        if (isStopped) throw IOException("Download cancelled")
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
        val notification = UpdateDownloadNotifications.progress(applicationContext, id, percent)
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
        private const val NOTIFICATION_ID = 2001
        private const val UNKNOWN_PROGRESS = -1
        const val INPUT_APK_URL = "apkUrl"
        const val INPUT_APK_NAME = "apkName"
        const val INPUT_VERSION = "version"
        const val INPUT_APK_SIZE = "apkSize"
        const val INPUT_APK_SHA256 = "apkSha256"
        const val PROGRESS_PERCENT = "progressPercent"
        const val PROGRESS_BYTES_DOWNLOADED = "progressBytesDownloaded"
        const val PROGRESS_TOTAL_BYTES = "progressTotalBytes"
        const val OUTPUT_APK_PATH = "apkPath"
        const val OUTPUT_APK_NAME = "apkName"
        const val OUTPUT_VERSION = "version"
        const val OUTPUT_APK_SHA256 = "apkSha256"
        const val OUTPUT_ERROR = "error"

        @JvmStatic
        fun enqueue(
            context: Context,
            apkUrl: String,
            apkName: String?,
            version: String,
            apkSize: Long,
            apkSha256: String
        ): UUID {
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_APK_URL to apkUrl,
                        INPUT_APK_NAME to apkName.orEmpty(),
                        INPUT_VERSION to version,
                        INPUT_APK_SIZE to apkSize,
                        INPUT_APK_SHA256 to apkSha256
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
            UpdateCacheCleanup.schedule(context)
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
