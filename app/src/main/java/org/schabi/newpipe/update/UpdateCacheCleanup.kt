package org.schabi.newpipe.update

import android.content.Context
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.BuildConfig

/** Removes obsolete updater APKs without expiring downloads of a newer release. */
object UpdateCacheCleanup {
    private const val TAG = "UpdateCacheCleanup"
    internal const val WORK_NAME = "update-cache-cleanup"
    internal const val DIRECTORY = "update"
    internal const val INSTALL_LEASES = "update_install_leases"
    internal val RECENT_FILE_GRACE = TimeUnit.DAYS.toMillis(1)
    internal val INSTALL_GRACE = TimeUnit.DAYS.toMillis(3)
    private val lock = Any()
    private val filename = Regex("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})-[A-Za-z0-9._-]+\\.apk")

    fun schedule(context: Context) {
        if (!File(context.cacheDir, DIRECTORY).isDirectory) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UpdateCacheCleanupWorker>()
                // APK inspection is maintenance work; keep it away from cold-start rendering.
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
        )
    }

    /** Persist protection before leaving for permission settings or Android's package installer. */
    @JvmStatic
    fun protectForInstallation(context: Context, apkPath: String): Boolean = protectForInstallation(context, File(apkPath))

    internal fun protectForInstallation(context: Context, file: File, now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        try {
            val directory = directory(context) ?: return@synchronized true
            if (ownedWorkId(directory, file) == null) return@synchronized true
            if (!file.isFile) return@synchronized false
            // commit() is intentional: the installing app process can be stopped immediately.
            context.getSharedPreferences(INSTALL_LEASES, Context.MODE_PRIVATE)
                .edit().putLong(file.name, now + INSTALL_GRACE).commit()
        } catch (error: Exception) {
            Log.w(TAG, "Could not protect the APK for installation", error)
            false
        }
    }

    @Suppress("DEPRECATION") // The integer-flags API also works on API 33+; no signing flags needed.
    internal fun clean(
        context: Context,
        workManager: WorkManager = WorkManager.getInstance(context),
        installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        now: Long = System.currentTimeMillis()
    ): Int {
        val directory = directory(context) ?: return 0
        val preferences = context.getSharedPreferences(INSTALL_LEASES, Context.MODE_PRIVATE)
        var removed = 0
        for (file in directory.listFiles().orEmpty()) {
            val workId = ownedWorkId(directory, file) ?: continue
            val modified = file.lastModified()
            val length = file.length()
            if (!oldEnough(modified, now) || length <= 0) continue
            try {
                val work = workManager.getWorkInfoById(workId).get()
                // A missing record can mean WorkManager already pruned an old finished job.
                if (work != null && (!work.state.isFinished || UpdateDownloadWorker::class.java.name !in work.tags)) continue
                val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: continue
                val versionCode = PackageInfoCompat.getLongVersionCode(archive)
                if (archive.packageName != context.packageName || versionCode <= 0 || versionCode > installedVersionCode) continue

                val deleted = synchronized(lock) {
                    // Check again after inspection: the installation UI may have leased the file.
                    val lease = preferences.getLong(file.name, 0L)
                    if (lease > now || file.lastModified() != modified || file.length() != length ||
                        ownedWorkId(directory, file) == null
                    ) {
                        false
                    } else {
                        file.delete()
                    }
                }
                if (deleted) {
                    removed++
                    UpdateDownloadNotifications.dismissCompleted(context, workId)
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (error: Exception) {
                // Failure to establish ownership, state or metadata must preserve the APK.
                Log.w(TAG, "Keeping an update APK whose cleanup eligibility could not be checked", error)
            }
        }
        synchronized(lock) {
            val expired = preferences.all.filter { (name, deadline) ->
                deadline is Long && (deadline <= now || !File(directory, name).exists())
            }.keys
            if (expired.isNotEmpty()) {
                preferences.edit().apply { expired.forEach(::remove) }.apply()
            }
        }
        return removed
    }

    private fun oldEnough(modified: Long, now: Long): Boolean = modified > 0 && now >= modified && now - modified >= RECENT_FILE_GRACE

    private fun directory(context: Context): File? = try {
        val directory = File(context.cacheDir.canonicalFile, DIRECTORY)
        directory.takeIf { it.isDirectory && it.canonicalFile == it }
    } catch (_: Exception) {
        null
    }

    private fun ownedWorkId(directory: File, file: File): UUID? = try {
        val match = filename.matchEntire(file.name)
        if (match == null || !file.isFile || file.canonicalFile != File(directory, file.name)) {
            null
        } else {
            UUID.fromString(match.groupValues[1])
        }
    } catch (_: Exception) {
        null
    }
}

class UpdateCacheCleanupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (isStopped) return Result.success()
        return try {
            UpdateCacheCleanup.clean(applicationContext)
            Result.success()
        } catch (error: Exception) {
            Log.w("UpdateCacheCleanup", "Update cache maintenance deferred", error)
            Result.success()
        }
    }
}
