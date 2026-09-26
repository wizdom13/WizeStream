/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

class SubscriptionSyncEngine internal constructor(
    private val store: SubscriptionSyncStore,
    private val enabled: () -> Boolean = { true }
) {
    fun isEnabled(): Boolean = enabled()
    internal fun createRequest(peerId: String): SubscriptionSyncRequest {
        ensureEnabled()
        store.reconcileLocalSubscriptions()
        val batch = store.getPendingChanges(
            peerId,
            MAX_SUBSCRIPTION_CHANGES_PER_BATCH
        )
        return SubscriptionSyncRequest(
            knownRevisions = store.getKnownRevisions(),
            changes = batch.changes,
            hasMore = batch.hasMore
        ).also(SubscriptionSyncValidation::validateRequest)
    }

    internal fun handleRequest(
        peerId: String,
        request: SubscriptionSyncRequest
    ): SubscriptionSyncResponse {
        return try {
            ensureEnabled()
            SubscriptionSyncValidation.validateRequest(request)
            store.reconcileLocalSubscriptions()
            store.applyChanges(request.changes)
            store.acknowledgePeer(peerId, request.knownRevisions)
            val batch = store.getPendingChanges(
                peerId,
                MAX_SUBSCRIPTION_CHANGES_PER_BATCH
            )
            SubscriptionSyncResponse(
                accepted = true,
                knownRevisions = store.getKnownRevisions(),
                changes = batch.changes,
                hasMore = batch.hasMore
            ).also(SubscriptionSyncValidation::validateResponse)
        } catch (error: Exception) {
            SubscriptionSyncResponse(
                accepted = false,
                error = (
                    error.message ?: "The subscription changes were rejected"
                    ).take(MAX_RESPONSE_ERROR_LENGTH)
            )
        }
    }

    internal fun handleResponse(
        peerId: String,
        response: SubscriptionSyncResponse
    ): SubscriptionApplyResult {
        SubscriptionSyncValidation.validateResponse(response)
        if (!response.accepted) {
            throw SubscriptionSyncException(
                response.error ?: "The remote device rejected synchronization"
            )
        }
        store.acknowledgePeer(peerId, response.knownRevisions)
        return store.applyChanges(response.changes)
    }

    internal fun clearPeerKnowledge() {
        store.clearPeerKnowledge()
    }

    private fun ensureEnabled() {
        if (!enabled()) {
            throw SubscriptionSyncException(
                "Subscription synchronization is disabled on this device"
            )
        }
    }

    companion object {
        private const val MAX_RESPONSE_ERROR_LENGTH = 512
    }
}
