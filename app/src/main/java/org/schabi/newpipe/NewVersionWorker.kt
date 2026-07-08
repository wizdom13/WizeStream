package org.schabi.newpipe

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.UUID
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.update.NewPipeMaterialUpdateRepository
import org.schabi.newpipe.update.NewPipeMaterialUpdateRepository.VersionComparison
import org.schabi.newpipe.util.ReleaseVersionUtil

class NewVersionWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private data class UpdateCheckResult(
        val release: NewPipeMaterialUpdateRepository.Release,
        val installedVersion: String,
        val comparison: VersionComparison
    )

    private fun handleUpdateCheckResult(result: UpdateCheckResult, isManual: Boolean) {
        if (!isManual && result.comparison == VersionComparison.NEWER) {
            showUpdateNotification(result.release, result.installedVersion)
        }
    }

    private fun showUpdateNotification(
        release: NewPipeMaterialUpdateRepository.Release,
        installedVersion: String
    ) {
        val intent = Intent(applicationContext, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_OPEN_UPDATE_SETTINGS, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntentCompat.getActivity(
            applicationContext,
            0,
            intent,
            0,
            false
        )
        val channelId = applicationContext.getString(R.string.app_update_notification_channel_id)
        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_newpipe_update)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setContentTitle(
                applicationContext.getString(R.string.app_update_available_notification_title)
            )
            .setContentText(
                applicationContext.getString(
                    R.string.app_update_available_notification_text_material,
                    release.version,
                    installedVersion
                )
            )

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(2000, notificationBuilder.build())
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    private fun checkNewVersion(): UpdateCheckResult? {
        val isManual = inputData.getBoolean(IS_MANUAL, false)
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        if (!isManual) {
            val expiry = prefs.getLong(applicationContext.getString(R.string.update_expiry_key), 0)
            if (!ReleaseVersionUtil.isLastUpdateCheckExpired(expiry)) {
                return null
            }
        }

        val releases = NewPipeMaterialUpdateRepository.fetchReleases()
        val release = NewPipeMaterialUpdateRepository.selectLatestCandidateRelease(releases)

        val newExpiry = ReleaseVersionUtil.coerceUpdateCheckExpiry(null)
        prefs.edit {
            putLong(applicationContext.getString(R.string.update_expiry_key), newExpiry)
            putLong(
                applicationContext.getString(R.string.latest_update_check_timestamp_key),
                System.currentTimeMillis()
            )
        }

        release ?: return null
        val installedVersion = NewPipeMaterialUpdateRepository.installedVersionName()
        val comparison = NewPipeMaterialUpdateRepository.compareInstalledToLatest(
            installedVersion,
            release.version
        )

        prefs.edit {
            putString(
                applicationContext.getString(R.string.latest_available_version_value_key),
                release.version
            )
            putString(
                applicationContext.getString(R.string.latest_available_release_url_key),
                release.htmlUrl
            )
            putString(
                applicationContext.getString(R.string.latest_available_changelog_key),
                release.body
            )
        }

        return UpdateCheckResult(release, installedVersion, comparison).also {
            handleUpdateCheckResult(it, isManual)
        }
    }

    override fun doWork(): Result {
        val isManual = inputData.getBoolean(IS_MANUAL, false)
        return try {
            val result = checkNewVersion()
            if (isManual && result == null) {
                Result.failure()
            } else {
                Result.success(result?.toOutputData() ?: Data.EMPTY)
            }
        } catch (e: IOException) {
            Log.w(
                TAG,
                "Could not fetch NewPipe Material GitHub releases: probably network problem",
                e
            )
            Result.failure()
        } catch (e: ReCaptchaException) {
            Log.e(TAG, "ReCaptchaException should never happen here.", e)
            Result.failure()
        } catch (e: Exception) {
            Log.w(TAG, "Could not check NewPipe Material GitHub releases", e)
            Result.failure()
        }
    }

    private fun UpdateCheckResult.toOutputData(): Data {
        return workDataOf(
            OUTPUT_COMPARISON to comparison.name,
            OUTPUT_LATEST_VERSION to release.version,
            OUTPUT_INSTALLED_VERSION to installedVersion,
            OUTPUT_RELEASE_URL to release.htmlUrl,
            OUTPUT_APK_URL to release.apkUrl.orEmpty(),
            OUTPUT_APK_NAME to release.apkName.orEmpty(),
            OUTPUT_APK_SIZE to (release.apkSize ?: -1L),
            OUTPUT_CHANGELOG to release.body,
            OUTPUT_RELEASE_TITLE to release.title,
            OUTPUT_PUBLISHED_AT to release.publishedAt
        )
    }

    companion object {
        private val TAG = NewVersionWorker::class.java.simpleName
        private const val IS_MANUAL = "isManual"
        const val OUTPUT_COMPARISON = "comparison"
        const val OUTPUT_LATEST_VERSION = "latestVersion"
        const val OUTPUT_INSTALLED_VERSION = "installedVersion"
        const val OUTPUT_RELEASE_URL = "releaseUrl"
        const val OUTPUT_APK_URL = "apkUrl"
        const val OUTPUT_APK_NAME = "apkName"
        const val OUTPUT_APK_SIZE = "apkSize"
        const val OUTPUT_CHANGELOG = "changelog"
        const val OUTPUT_RELEASE_TITLE = "releaseTitle"
        const val OUTPUT_PUBLISHED_AT = "publishedAt"

        /**
         * Start a worker which checks GitHub Releases for NewPipe Material updates.
         * Manual checks bypass the stored expiry timestamp, while automatic checks respect it
         * to avoid contacting GitHub on every app launch.
         */
        @JvmStatic
        fun enqueueNewVersionCheckingWork(context: Context, isManual: Boolean): UUID {
            val workRequest = OneTimeWorkRequestBuilder<NewVersionWorker>()
                .setInputData(workDataOf(IS_MANUAL to isManual))
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            return workRequest.id
        }
    }
}
