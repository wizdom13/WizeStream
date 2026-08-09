/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

/** JVM-side copies of the portable store boundaries used by the shared sync engines. */
internal interface SubscriptionSyncStore {
    val localPeerId: String
    fun reconcileLocalSubscriptions()
    fun getKnownRevisions(): Map<String, Long>
    fun getPendingChanges(peerId: String, limit: Int): SubscriptionChangeBatch
    fun acknowledgePeer(peerId: String, knownRevisions: Map<String, Long>)
    fun applyChanges(changes: List<SubscriptionChange>): SubscriptionApplyResult
    fun clearPeerKnowledge()
}

internal interface PlaylistSyncStore {
    val localPeerId: String
    fun reconcileLocalPlaylists()
    fun getKnownRevisions(): Map<String, Long>
    fun getPendingChanges(peerId: String, limit: Int): PlaylistChangeBatch
    fun acknowledgePeer(peerId: String, knownRevisions: Map<String, Long>)
    fun applyChanges(changes: List<PlaylistChange>): PlaylistApplyResult
    fun clearPeerKnowledge()
}

internal interface HistorySyncStore {
    val localPeerId: String
    fun reconcileLocal(category: HistorySyncCategory)
    fun getKnownRevisions(category: HistorySyncCategory): Map<String, Long>
    fun getPendingChanges(
        category: HistorySyncCategory,
        peerId: String,
        limit: Int
    ): HistoryChangeBatch
    fun acknowledgePeer(
        category: HistorySyncCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    )
    fun applyChanges(
        category: HistorySyncCategory,
        changes: List<HistoryChange>
    ): HistoryApplyResult
    fun clearPeerKnowledge()
}

internal interface StructuredPreferenceSyncStore {
    val localPeerId: String
    fun reconcileLocal(category: StructuredPreferenceCategory)
    fun getKnownRevisions(category: StructuredPreferenceCategory): Map<String, Long>
    fun getPendingChanges(
        category: StructuredPreferenceCategory,
        peerId: String,
        limit: Int
    ): StructuredPreferenceChangeBatch
    fun acknowledgePeer(
        category: StructuredPreferenceCategory,
        peerId: String,
        knownRevisions: Map<String, Long>
    )
    fun applyChanges(
        category: StructuredPreferenceCategory,
        changes: List<StructuredPreferenceChange>
    ): StructuredPreferenceApplyResult
    fun clearPeerKnowledge()
}
