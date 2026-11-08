/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySyncResponseWaiterTest {
    @Test
    fun watchProcessingHasMoreTimeWithoutExtendingOtherCategories() {
        assertEquals(120_000L, HistorySyncResponseWaiter.timeoutMillis(HistorySyncCategory.WATCH))
        assertEquals(30_000L, HistorySyncResponseWaiter.timeoutMillis(HistorySyncCategory.SEARCH))
        assertEquals(30_000L, HistorySyncResponseWaiter.timeoutMillis(HistorySyncCategory.LEARNING_NOTES))
    }

    @Test
    fun timeoutReportsCategoryBatchAndProcessingStageWithoutAcknowledgingAnything() {
        val response = CompletableFuture<HistorySyncResponse>()
        val error = assertThrows(HistorySyncException::class.java) {
            HistorySyncResponseWaiter.await(response, "Tablet", HistorySyncCategory.WATCH, 3, 8, 1)
        }
        assertTrue(error.cause is TimeoutException)
        assertTrue(error.message.orEmpty().contains("WATCH history response from Tablet"))
        assertTrue(error.message.orEmpty().contains("batch 3, 8 outgoing changes"))
        assertFalse(response.isDone)
        assertTrue(DeviceSyncTransportRecovery.shouldRetryTransportFailure(error))
    }

    @Test
    fun delayedProcessingCanCompleteWithinItsBudget() {
        val response = CompletableFuture<HistorySyncResponse>()
        val entered = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        try {
            val result = caller.submit<HistorySyncResponse> {
                entered.countDown()
                HistorySyncResponseWaiter.await(response, "Tablet", HistorySyncCategory.WATCH, 1, 2, 5_000)
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertFalse(result.isDone)
            val expected = HistorySyncResponse(accepted = true, category = HistorySyncCategory.WATCH)
            response.complete(expected)
            assertSame(expected, result.get(1, TimeUnit.SECONDS))
        } finally {
            caller.shutdownNow()
        }
    }
}
