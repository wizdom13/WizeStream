package org.schabi.newpipe.player.datasource

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.DownloaderImpl
import java.io.IOException
import java.util.concurrent.TimeUnit

class SabrPolicyUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val url = BuildConfig.SABR_POLICY_URL
        if (url.isEmpty() || BuildConfig.SABR_POLICY_PUBLIC_KEY_BASE64.isEmpty()) {
            return Result.success()
        }
        return try {
            val response = DownloaderImpl.getInstance().get(url)
            when (response.responseCode()) {
                200 -> {
                    val body = response.rawResponseBody()
                        ?: throw IOException("SABR policy response had no body")
                    SabrPolicyRuntime.installDocument(body, System.currentTimeMillis())
                    Result.success()
                }
                204, 304 -> Result.success()
                in 500..599 -> Result.retry()
                else -> {
                    Log.w(TAG, "SABR policy update failed with HTTP ${response.responseCode()}")
                    Result.failure()
                }
            }
        } catch (error: IOException) {
            Log.w(TAG, "Could not update SABR policy", error)
            Result.retry()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Rejected SABR policy update", error)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "SabrPolicyUpdate"
        private const val IMMEDIATE_WORK = "sabr-policy-update-now"
        private const val PERIODIC_WORK = "sabr-policy-update-periodic"

        @JvmStatic
        fun initialize(context: Context) {
            if (BuildConfig.SABR_POLICY_URL.isEmpty() ||
                BuildConfig.SABR_POLICY_PUBLIC_KEY_BASE64.isEmpty()
            ) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val immediate = OneTimeWorkRequestBuilder<SabrPolicyUpdateWorker>()
                .setConstraints(constraints)
                .build()
            val periodic = PeriodicWorkRequestBuilder<SabrPolicyUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                immediate,
            )
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )
        }
    }
}
