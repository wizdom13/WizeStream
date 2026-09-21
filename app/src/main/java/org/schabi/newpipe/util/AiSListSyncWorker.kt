/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class AiSListSyncWorker(
    context: Context,
    workerParameters: WorkerParameters
) : Worker(context, workerParameters) {
    override fun doWork(): Result {
        if (!AiSListRepository.isEnabled(applicationContext)) {
            return Result.success()
        }

        return try {
            val status = AiSListRepository.sync(applicationContext)
            Result.success(
                workDataOf(
                    OUTPUT_BLOCK_COUNT to status.blockCount,
                    OUTPUT_WARN_COUNT to status.warnCount,
                    OUTPUT_COUNT to status.count,
                    OUTPUT_UPDATED_AT to status.updatedAtMillis
                )
            )
        } catch (_: IOException) {
            Result.failure()
        } catch (_: RuntimeException) {
            Result.failure()
        }
    }

    companion object {
        const val OUTPUT_BLOCK_COUNT = "blockCount"
        const val OUTPUT_WARN_COUNT = "warnCount"
        const val OUTPUT_COUNT = "count"
        const val OUTPUT_UPDATED_AT = "updatedAt"

        private const val PERIODIC_WORK_NAME = "aislist-periodic-sync"
        private const val IMMEDIATE_WORK_NAME = "aislist-immediate-sync"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        @JvmStatic
        fun initialize(context: Context) {
            if (AiSListRepository.isEnabled(context)) {
                schedulePeriodic(context)
                if (AiSListRepository.status(context).count == 0) {
                    enqueueImmediateSync(context)
                }
            }
        }

        @JvmStatic
        fun setEnabled(context: Context, enabled: Boolean): UUID? {
            AiSListRepository.setEnabled(context, enabled)
            val workManager = WorkManager.getInstance(context)
            return if (enabled) {
                schedulePeriodic(context)
                enqueueImmediateSync(context)
            } else {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
                null
            }
        }

        @JvmStatic
        fun enqueueImmediateSync(context: Context): UUID {
            val request = OneTimeWorkRequestBuilder<AiSListSyncWorker>()
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            return request.id
        }

        private fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AiSListSyncWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
