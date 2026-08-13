package org.schabi.newpipe.sync

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomaticSyncPolicyTest {
    @Test
    fun `automatic policy is private opt in and excludes search history by default`() {
        val policy = AutomaticSyncPolicy()
        assertFalse(policy.enabled)
        assertEquals(60, policy.intervalMinutes)
        assertFalse("searchHistory" in policy.categories)
        assertTrue(policy.asMap()["localNetworkOnly"] == true)
    }

    @Test
    fun `enabled policy requires a valid interval category and trusted device`() {
        assertFailsWith<IllegalArgumentException> {
            AutomaticSyncPolicy(enabled = true).validate()
        }
        assertFailsWith<IllegalArgumentException> {
            AutomaticSyncPolicy(
                enabled = true,
                intervalMinutes = 5,
                categories = listOf("subscriptions"),
                peerIds = listOf("peer")
            ).validate()
        }
        AutomaticSyncPolicy(
            enabled = true,
            intervalMinutes = 15,
            categories = listOf("subscriptions"),
            peerIds = listOf("peer")
        ).validate()
    }

    @Test
    fun `retry delay follows the approved capped progression with deterministic jitter`() {
        val delays = (1..8).map { DesktopSyncLogRepository.retryDelayMillis("peer-a", it) }
        assertTrue(delays.zipWithNext().all { (first, second) -> second >= first })
        assertTrue(delays.first() in 5 * 60_000L..6 * 60_000L)
        assertTrue(delays.last() in 6 * 60 * 60_000L..(6 * 60 * 60_000L * 120 / 100))
        assertEquals(delays.last(), DesktopSyncLogRepository.retryDelayMillis("peer-a", 20))
    }

    @Test
    fun `automatic run never overlaps an active manual run`() {
        val coordinator = DesktopSyncRunCoordinator()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val manual = executor.submit {
            coordinator.manual("manual") {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertEquals(null, coordinator.automatic("automatic", 1L) { "unexpected" })
        assertTrue(coordinator.status()["running"] == true)
        release.countDown()
        manual.get(5, TimeUnit.SECONDS)
        executor.shutdownNow()
        assertTrue(coordinator.status()["running"] == false)
    }
}
