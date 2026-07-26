/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

class StructuredPreferenceSyncEngine internal constructor(
    private val store: StructuredPreferenceSyncStore
) {
    internal fun createRequest(
        peerId: String,
        category: StructuredPreferenceCategory
    ): StructuredPreferenceSyncRequest {
        store.reconcileLocal(category)
        val batch = store.getPendingChanges(
            category,
            peerId,
            MAX_STRUCTURED_PREFERENCE_CHANGES_PER_BATCH
        )
        return StructuredPreferenceSyncRequest(
            category = category,
            knownRevisions = store.getKnownRevisions(category),
            changes = batch.changes,
            hasMore = batch.hasMore
        ).also(StructuredPreferenceSyncValidation::validateRequest)
    }

    internal fun handleRequest(
        peerId: String,
        request: StructuredPreferenceSyncRequest
    ): StructuredPreferenceSyncResponse {
        return try {
            StructuredPreferenceSyncValidation.validateRequest(request)
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
                MAX_STRUCTURED_PREFERENCE_CHANGES_PER_BATCH
            )
            StructuredPreferenceSyncResponse(
                accepted = true,
                category = request.category,
                knownRevisions = store.getKnownRevisions(request.category),
                changes = batch.changes,
                hasMore = batch.hasMore
            ).also { response ->
                StructuredPreferenceSyncValidation.validateResponse(
                    request.category,
                    response
                )
            }
        } catch (error: Exception) {
            StructuredPreferenceSyncResponse(
                accepted = false,
                category = request.category,
                error = (
                    error.message ?: "The structured preference changes were rejected"
                    ).take(MAX_RESPONSE_ERROR_LENGTH)
            )
        }
    }

    internal fun handleResponse(
        peerId: String,
        category: StructuredPreferenceCategory,
        response: StructuredPreferenceSyncResponse
    ): StructuredPreferenceApplyResult {
        StructuredPreferenceSyncValidation.validateResponse(category, response)
        if (!response.accepted) {
            throw StructuredPreferenceSyncException(
                response.error
                    ?: "The remote device rejected structured preference synchronization"
            )
        }
        val applied = store.applyChanges(category, response.changes)
        store.acknowledgePeer(category, peerId, response.knownRevisions)
        return applied
    }

    internal fun clearPeerKnowledge() {
        store.clearPeerKnowledge()
    }

    companion object {
        private const val MAX_RESPONSE_ERROR_LENGTH = 512
    }
}
