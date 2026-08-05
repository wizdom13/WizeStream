/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

class HistorySyncEngine internal constructor(
    private val store: HistorySyncStore,
    private val categoryEnabled: (HistorySyncCategory) -> Boolean = { true }
) {
    fun isEnabled(category: HistorySyncCategory): Boolean = categoryEnabled(category)

    internal fun createRequest(
        peerId: String,
        category: HistorySyncCategory
    ): HistorySyncRequest {
        ensureEnabled(category)
        store.reconcileLocal(category)
        val batch = store.getPendingChanges(
            category,
            peerId,
            MAX_HISTORY_CHANGES_PER_BATCH
        )
        return HistorySyncRequest(
            category = category,
            knownRevisions = store.getKnownRevisions(category),
            changes = batch.changes,
            hasMore = batch.hasMore
        ).also(HistorySyncValidation::validateRequest)
    }

    internal fun handleRequest(
        peerId: String,
        request: HistorySyncRequest
    ): HistorySyncResponse {
        return try {
            ensureEnabled(request.category)
            HistorySyncValidation.validateRequest(request)
            store.reconcileLocal(request.category)
            store.applyChanges(request.category, request.changes)
            store.acknowledgePeer(
                request.category,
                peerId,
                request.knownRevisions
            )
            val batch = store.getPendingChanges(
                request.category,
                peerId,
                MAX_HISTORY_CHANGES_PER_BATCH
            )
            HistorySyncResponse(
                accepted = true,
                category = request.category,
                knownRevisions = store.getKnownRevisions(request.category),
                changes = batch.changes,
                hasMore = batch.hasMore
            ).also { response ->
                HistorySyncValidation.validateResponse(request.category, response)
            }
        } catch (error: Exception) {
            HistorySyncResponse(
                accepted = false,
                category = request.category,
                error = (
                    error.message ?: "The history changes were rejected"
                    ).take(MAX_RESPONSE_ERROR_LENGTH)
            )
        }
    }

    internal fun handleResponse(
        peerId: String,
        category: HistorySyncCategory,
        response: HistorySyncResponse
    ): HistoryApplyResult {
        HistorySyncValidation.validateResponse(category, response)
        if (!response.accepted) {
            throw HistorySyncException(
                response.error ?: "The remote device rejected history synchronization"
            )
        }
        val applied = store.applyChanges(category, response.changes)
        store.acknowledgePeer(category, peerId, response.knownRevisions)
        return applied
    }

    internal fun clearPeerKnowledge() {
        store.clearPeerKnowledge()
    }

    private fun ensureEnabled(category: HistorySyncCategory) {
        if (!categoryEnabled(category)) {
            val label = when (category) {
                HistorySyncCategory.WATCH -> "Watch history"
                HistorySyncCategory.SEARCH -> "Search history"
                HistorySyncCategory.LEARNING_NOTES -> "Learning notes"
            }
            throw HistorySyncException("$label synchronization is disabled on this device")
        }
    }

    companion object {
        private const val MAX_RESPONSE_ERROR_LENGTH = 512
    }
}
