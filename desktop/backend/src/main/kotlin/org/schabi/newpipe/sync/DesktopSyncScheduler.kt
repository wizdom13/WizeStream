/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

internal class DesktopSyncScheduler(
    private val repository: DesktopSyncLogRepository,
    private val network: DesktopNetworkEligibility,
    private val automaticRun: (AutomaticSyncPolicy, Long, Boolean) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "wizestream-automatic-sync").apply { isDaemon = true }
    },
    private val startupJitterMillis: () -> Long = {
        ThreadLocalRandom.current().nextLong(30_000, 90_001)
    }
) {
    @Volatile
    private var stopped = false
    private var future: ScheduledFuture<*>? = null

    @Synchronized
    fun start() {
        stopped = false
        val policy = repository.policy()
        if (!policy.enabled) {
            repository.updateSchedule(null, null)
            return
        }
        val now = clock.millis()
        val persisted = repository.scheduleState()["nextRunAtEpochMillis"] as? Long
        val nextRun = persisted ?: now + intervalMillis(policy)
        val wake = if (nextRun <= now) now + startupJitterMillis() else nextRun
        repository.updateSchedule(nextRun, wake)
        schedule(wake)
    }

    @Synchronized
    fun policyChanged() {
        future?.cancel(false)
        val policy = repository.policy()
        if (!policy.enabled || stopped) {
            repository.updateSchedule(null, null)
            return
        }
        val next = clock.millis() + intervalMillis(policy)
        repository.updateSchedule(next, next)
        schedule(next)
    }

    @Synchronized
    fun stop() {
        stopped = true
        future?.cancel(false)
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun wake() {
        if (stopped) return
        val now = clock.millis()
        val policy = repository.policy()
        if (!policy.enabled) return
        val eligible = network.isEligible()
        automaticRun(policy, now, eligible)
        val state = repository.scheduleState()
        val busy = state["lastOutcome"] == SyncRunOutcome.SKIPPED_BUSY.name.lowercase()
        val regular = (state["nextRunAtEpochMillis"] as? Long)?.let {
            if (it <= now && eligible && !busy) now + intervalMillis(policy) else it
        } ?: now + intervalMillis(policy)
        val next = if (!eligible || busy) now + SHORT_RETRY_MILLIS else {
            listOfNotNull(regular, repository.earliestRetry(policy.peerIds)).min()
        }
        repository.updateSchedule(regular, next)
        synchronized(this) { if (!stopped) schedule(next) }
    }

    private fun schedule(epochMillis: Long) {
        future = executor.schedule(
            { runCatching(::wake).onFailure { it.printStackTrace(System.err) } },
            (epochMillis - clock.millis()).coerceAtLeast(0),
            TimeUnit.MILLISECONDS
        )
    }

    companion object {
        private const val SHORT_RETRY_MILLIS = 5 * 60_000L
        internal fun intervalMillis(policy: AutomaticSyncPolicy): Long =
            policy.intervalMinutes * 60_000L
    }
}
