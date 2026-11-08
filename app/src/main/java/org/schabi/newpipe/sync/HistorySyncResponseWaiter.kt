/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:Suppress("NewApi")

package org.schabi.newpipe.sync

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Database reconciliation is part of the response, not connection establishment. */
internal object HistorySyncResponseWaiter {
    private const val WATCH_RESPONSE_TIMEOUT_MILLIS = 120_000L
    private const val OTHER_RESPONSE_TIMEOUT_MILLIS = 30_000L

    fun timeoutMillis(category: HistorySyncCategory): Long = if (category == HistorySyncCategory.WATCH) WATCH_RESPONSE_TIMEOUT_MILLIS else OTHER_RESPONSE_TIMEOUT_MILLIS

    fun await(
        response: CompletableFuture<HistorySyncResponse>,
        peerName: String,
        category: HistorySyncCategory,
        round: Int,
        sentChanges: Int,
        responseTimeoutMillis: Long = timeoutMillis(category)
    ): HistorySyncResponse {
        try {
            return response.get(responseTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            throw HistorySyncException(
                "${category.name} history response from $peerName timed out after ${responseTimeoutMillis}ms " +
                    "(batch $round, $sentChanges outgoing changes); the peer may still be processing history",
                error
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw HistorySyncException("History synchronization was interrupted", error)
        } catch (error: Exception) {
            throw HistorySyncException(
                "${category.name} history response from $peerName failed (batch $round, $sentChanges outgoing changes)",
                error
            )
        }
    }
}
