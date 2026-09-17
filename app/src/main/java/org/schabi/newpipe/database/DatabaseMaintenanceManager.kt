/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database

import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import android.util.Log
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Callable
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.sync.DeviceSyncManager

data class DatabaseCleanupResult(
    val feedRows: Int,
    val feedUpdateRows: Int,
    val orphanStreamRows: Int,
    val syncRows: Int
) {
    val deletedRows: Int
        get() = feedRows + feedUpdateRows + orphanStreamRows + syncRows
}

class DatabaseMaintenanceManager(context: Context) {
    companion object {
        private const val TAG = "DatabaseMaintenance"

        internal fun runBestEffortMaintenance(maintenance: () -> Unit): Boolean {
            return try {
                maintenance()
                true
            } catch (_: SQLiteDatabaseLockedException) {
                false
            }
        }
    }

    private val applicationContext = context.applicationContext
    private val database = NewPipeDatabase.getInstance(applicationContext)

    fun clearPersistentCaches(): Single<DatabaseCleanupResult> {
        return Single.fromCallable {
            val result = database.runInTransaction(
                Callable {
                    val feedRows = database.feedDAO().deleteAll()
                    val feedUpdateRows = database.feedDAO().deleteAllLastUpdated()
                    val orphanStreamRows = database.streamDAO().deleteOrphans()
                    val syncRows = resetSyncJournalsWhenUnpaired()
                    DatabaseCleanupResult(
                        feedRows,
                        feedUpdateRows,
                        orphanStreamRows,
                        syncRows
                    )
                }
            )
            val maintenanceCompleted = runBestEffortMaintenance {
                database.openHelper.writableDatabase.apply {
                    query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                    execSQL("PRAGMA optimize")
                }
            }
            if (!maintenanceCompleted) {
                Log.w(
                    TAG,
                    "Skipped optional database checkpoint/optimization because the database is busy"
                )
            }
            result
        }.subscribeOn(Schedulers.io())
    }

    fun compactUnpairedSyncJournals(): Single<Int> {
        return Single.fromCallable {
            database.runInTransaction(
                Callable {
                    resetSyncJournalsWhenUnpaired()
                }
            )
        }.subscribeOn(Schedulers.io())
    }

    private fun resetSyncJournalsWhenUnpaired(): Int {
        if (DeviceSyncManager.hasTrustedPeers(applicationContext)) {
            return 0
        }

        val subscriptionDao = database.subscriptionSyncDAO()
        val historyDao = database.historySyncDAO()
        return subscriptionDao.deleteAllChanges() +
            subscriptionDao.deleteAllRecords() +
            subscriptionDao.deleteAllOriginStates() +
            historyDao.deleteAllChanges() +
            historyDao.deleteAllRecords() +
            historyDao.deleteAllOriginStates() +
            historyDao.deleteAllPeerStates()
    }
}
