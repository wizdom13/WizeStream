/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

class PlaylistSyncEngine internal constructor(
    private val store: PlaylistSyncStore
) {
    internal fun createRequest(peerId: String): PlaylistSyncRequest {
        store.reconcileLocalPlaylists()
        val batch = store.getPendingChanges(
            peerId,
            MAX_PLAYLIST_CHANGES_PER_BATCH
        )
        return PlaylistSyncRequest(
            knownRevisions = store.getKnownRevisions(),
            changes = batch.changes,
            hasMore = batch.hasMore
        ).also(PlaylistSyncValidation::validateRequest)
    }

    internal fun handleRequest(
        peerId: String,
        request: PlaylistSyncRequest
    ): PlaylistSyncResponse {
        return try {
            PlaylistSyncValidation.validateRequest(request)
            store.reconcileLocalPlaylists()
            store.applyChanges(request.changes)
            store.acknowledgePeer(peerId, request.knownRevisions)
            val batch = store.getPendingChanges(
                peerId,
                MAX_PLAYLIST_CHANGES_PER_BATCH
            )
            PlaylistSyncResponse(
                accepted = true,
                knownRevisions = store.getKnownRevisions(),
                changes = batch.changes,
                hasMore = batch.hasMore
            ).also(PlaylistSyncValidation::validateResponse)
        } catch (error: Exception) {
            PlaylistSyncResponse(
                accepted = false,
                error = (
                    error.message ?: "The playlist changes were rejected"
                    ).take(MAX_RESPONSE_ERROR_LENGTH)
            )
        }
    }

    internal fun handleResponse(
        peerId: String,
        response: PlaylistSyncResponse
    ): PlaylistApplyResult {
        PlaylistSyncValidation.validateResponse(response)
        if (!response.accepted) {
            throw PlaylistSyncException(
                response.error ?: "The remote device rejected playlist synchronization"
            )
        }
        val applied = store.applyChanges(response.changes)
        store.acknowledgePeer(peerId, response.knownRevisions)
        return applied
    }

    internal fun clearPeerKnowledge() {
        store.clearPeerKnowledge()
    }

    companion object {
        private const val MAX_RESPONSE_ERROR_LENGTH = 512
    }
}
