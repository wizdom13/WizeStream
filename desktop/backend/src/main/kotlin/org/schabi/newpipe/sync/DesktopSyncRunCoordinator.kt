/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.util.concurrent.locks.ReentrantLock

internal class DesktopSyncBusyException : IllegalStateException(
    "Another synchronization is already running"
)

internal class DesktopSyncRunCoordinator {
    private val lock = ReentrantLock(true)

    @Volatile
    private var active: ActiveRun? = null

    fun <T> manual(runId: String, block: () -> T): T {
        if (!lock.tryLock()) throw DesktopSyncBusyException()
        return runLocked(ActiveRun(runId, SyncRunTrigger.MANUAL, System.currentTimeMillis()), block)
    }

    fun <T> automatic(runId: String, startedAt: Long, block: () -> T): T? {
        if (!lock.tryLock()) return null
        return runLocked(ActiveRun(runId, SyncRunTrigger.AUTOMATIC, startedAt), block)
    }

    fun status(): Map<String, Any?> = active?.let {
        linkedMapOf(
            "running" to true,
            "runId" to it.runId,
            "trigger" to it.trigger.name.lowercase(),
            "startedAtEpochMillis" to it.startedAt
        )
    } ?: linkedMapOf("running" to false)

    private fun <T> runLocked(value: ActiveRun, block: () -> T): T {
        active = value
        return try {
            block()
        } finally {
            active = null
            lock.unlock()
        }
    }

    private data class ActiveRun(
        val runId: String,
        val trigger: SyncRunTrigger,
        val startedAt: Long
    )
}
