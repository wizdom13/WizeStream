/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

/** Bounds early URL refreshes when a live media segment is repeatedly rejected. */
internal class LiveMedia403RecoveryPolicy {
    private data class Failure(
        val mediaStartMs: Long,
        val firstAtMs: Long,
        val lastAtMs: Long,
        val count: Int
    )

    private var currentKey: String? = null
    private val failures = mutableMapOf<Int, Failure>()
    private var refreshesWithoutStablePlayback = 0
    private var lastRefreshAtMs = Long.MIN_VALUE
    private var healthySinceMs: Long? = null

    fun onRejectedSegment(
        key: String,
        trackType: Int,
        mediaStartMs: Long,
        nowMs: Long
    ): Boolean {
        select(key)
        healthySinceMs = null
        val previous = failures[trackType]
        val failure = if (previous == null || previous.mediaStartMs != mediaStartMs ||
            nowMs < previous.lastAtMs || nowMs - previous.firstAtMs > ERROR_WINDOW_MS
        ) {
            Failure(mediaStartMs, nowMs, nowMs, 1)
        } else {
            previous.copy(lastAtMs = nowMs, count = previous.count + 1)
        }
        failures[trackType] = failure
        if (failure.count < MIN_ERRORS || nowMs - failure.firstAtMs < MIN_ERROR_SPAN_MS ||
            refreshesWithoutStablePlayback >= MAX_EARLY_REFRESHES ||
            (lastRefreshAtMs != Long.MIN_VALUE && nowMs - lastRefreshAtMs < REFRESH_COOLDOWN_MS)
        ) {
            return false
        }

        failures.clear()
        lastRefreshAtMs = nowMs
        refreshesWithoutStablePlayback++
        return true
    }

    fun onLoadedSegment(
        key: String,
        trackType: Int,
        mediaStartMs: Long,
        nowMs: Long,
        isReady: Boolean = true
    ) {
        select(key)
        if (failures[trackType]?.mediaStartMs == mediaStartMs) failures.remove(trackType)
        if (!isReady) {
            healthySinceMs = null
            return
        }
        val healthySince = healthySinceMs
        if (healthySince == null) {
            healthySinceMs = nowMs
        } else if (nowMs - healthySince >= STABLE_PLAYBACK_MS) {
            refreshesWithoutStablePlayback = 0
        }
    }

    fun clearFailureWindow() {
        failures.clear()
        healthySinceMs = null
    }

    fun reset() {
        currentKey = null
        failures.clear()
        refreshesWithoutStablePlayback = 0
        lastRefreshAtMs = Long.MIN_VALUE
        healthySinceMs = null
    }

    private fun select(key: String) {
        if (key != currentKey) {
            reset()
            currentKey = key
        }
    }

    private companion object {
        const val MIN_ERRORS = 3
        const val MIN_ERROR_SPAN_MS = 500L
        const val ERROR_WINDOW_MS = 5_000L
        const val REFRESH_COOLDOWN_MS = 15_000L
        const val STABLE_PLAYBACK_MS = 30_000L
        const val MAX_EARLY_REFRESHES = 2
    }
}
