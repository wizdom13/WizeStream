/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Keeps database reconciliation off libp2p's network event loops. */
internal class SyncRequestDispatcher {
    @Volatile
    private var executor: ThreadPoolExecutor? = null

    @Synchronized
    fun start() {
        if (executor != null) return
        executor = ThreadPoolExecutor(
            2,
            2,
            30,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(32),
            { task -> Thread(task, "sync-responder").apply { isDaemon = true } }
        ).apply { allowCoreThreadTimeOut(true) }
    }

    fun execute(onFailure: () -> Unit, operation: () -> Unit) {
        val task = RequestTask(onFailure, operation)
        try {
            val current = executor ?: throw RejectedExecutionException("Listener stopped")
            current.execute(task)
        } catch (_: RejectedExecutionException) {
            task.reject()
        }
    }

    @Synchronized
    fun stop() {
        val current = executor
        executor = null
        current?.shutdownNow()?.forEach { (it as RequestTask).reject() }
    }

    private class RequestTask(
        private val onFailure: () -> Unit,
        private val operation: () -> Unit
    ) : Runnable {
        override fun run() {
            try {
                operation()
            } catch (_: Exception) {
                reject()
            }
        }

        fun reject() {
            runCatching(onFailure)
        }
    }
}
