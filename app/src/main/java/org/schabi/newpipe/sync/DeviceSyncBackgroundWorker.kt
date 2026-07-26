/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.App
import org.schabi.newpipe.R

class DeviceSyncBackgroundWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        if (!hasLocalNetwork(applicationContext)) {
            return Result.success()
        }

        val manager = DeviceSyncManager.get(applicationContext)
        if (manager.trustedPeers.isEmpty()) {
            return Result.success()
        }

        return try {
            val summary = manager.syncInBackground()
            Log.i(
                TAG,
                "Background synchronization completed: " +
                    "${summary.succeeded} succeeded, ${summary.failed} unavailable"
            )
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "Background synchronization could not run", error)
            Result.success()
        }
    }

    private fun hasLocalNetwork(context: Context): Boolean {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return DeviceSyncBackgroundPolicy.hasLocalTransport(
            wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            ethernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        )
    }

    companion object {
        private const val TAG = "DeviceSyncWorker"
    }
}

object DeviceSyncBackgroundScheduler {
    fun initialize(context: Context, hasTrustedPeers: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = preferences.getBoolean(
            context.getString(R.string.device_sync_background_key),
            true
        )
        setEnabled(context, enabled, hasTrustedPeers)
    }

    fun setEnabled(context: Context, enabled: Boolean, hasTrustedPeers: Boolean) {
        if (!DeviceSyncBackgroundPolicy.shouldSchedule(enabled, hasTrustedPeers)) {
            cancel(context)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequest.Builder(
            DeviceSyncBackgroundWorker::class.java,
            DeviceSyncBackgroundPolicy.REPEAT_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(WORK_NAME)
    }

    private const val WORK_NAME = App.PACKAGE_NAME + "_device_sync_background"
}

internal object DeviceSyncBackgroundPolicy {
    const val REPEAT_INTERVAL_HOURS = 1L

    fun shouldSchedule(enabled: Boolean, hasTrustedPeers: Boolean): Boolean {
        return enabled && hasTrustedPeers
    }

    fun hasLocalTransport(wifi: Boolean, ethernet: Boolean): Boolean {
        return wifi || ethernet
    }
}
