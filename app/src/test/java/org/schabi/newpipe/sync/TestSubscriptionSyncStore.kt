/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import org.schabi.newpipe.database.subscription.SubscriptionEntity

internal class TestSubscriptionSyncStore(
    override val localPeerId: String
) : SubscriptionSyncStore {
    private val journal = linkedMapOf<Pair<String, Long>, SubscriptionChange>()
    private val knownRevisions = linkedMapOf<String, Long>()
    private val peerKnowledge = linkedMapOf<String, MutableMap<String, Long>>()
    private val records = linkedMapOf<String, SubscriptionChange>()
    private val subscriptions = linkedMapOf<String, SyncedSubscription>()
    private var localRevision = 0L
    private var lamportVersion = 0L

    val subscriptionUrls: Set<String>
        get() = subscriptions.values.map(SyncedSubscription::url).toSet()

    fun youtubeModeMask(url: String): Int? {
        return subscriptions.values.firstOrNull { it.url == url }?.youtubeModeMask
    }

    override fun reconcileLocalSubscriptions() = Unit

    override fun recordLocalUpsert(subscription: SubscriptionEntity) {
        val synced = SyncedSubscription.from(subscription)
        subscriptions[recordId(synced.serviceId, synced.url)] = synced
        recordLocalChange(
            synced.serviceId,
            synced.url,
            SubscriptionChangeType.UPSERT,
            synced
        )
    }

    override fun recordLocalDelete(serviceId: Int, url: String) {
        subscriptions.remove(recordId(serviceId, url))
        recordLocalChange(
            serviceId,
            url,
            SubscriptionChangeType.DELETE,
            null
        )
    }

    fun add(serviceId: Int, url: String, name: String = url) {
        recordLocalUpsert(
            SubscriptionEntity(
                serviceId = serviceId,
                url = url,
                name = name
            )
        )
    }

    fun delete(serviceId: Int, url: String) {
        recordLocalDelete(serviceId, url)
    }

    override fun getKnownRevisions(): Map<String, Long> {
        return knownRevisions.toMap()
    }

    override fun getPendingChanges(
        peerId: String,
        limit: Int
    ): SubscriptionChangeBatch {
        val acknowledged = peerKnowledge[peerId].orEmpty()
        val pending = journal.values
            .filter { change ->
                change.originRevision > (acknowledged[change.originPeerId] ?: 0)
            }
            .sortedBy(SubscriptionChange::versionStamp)
        return SubscriptionChangeBatch(
            changes = pending.take(limit),
            hasMore = pending.size > limit
        )
    }

    override fun acknowledgePeer(
        peerId: String,
        knownRevisions: Map<String, Long>
    ) {
        val knowledge = peerKnowledge.getOrPut(peerId) { linkedMapOf() }
        knownRevisions.forEach { (origin, revision) ->
            val safeRevision = minOf(revision, this.knownRevisions[origin] ?: 0)
            knowledge[origin] = maxOf(knowledge[origin] ?: 0, safeRevision)
        }
    }

    override fun applyChanges(changes: List<SubscriptionChange>): SubscriptionApplyResult {
        SubscriptionSyncValidation.validateChanges(changes)
        var accepted = 0
        var added = 0
        var removed = 0
        changes.forEach { change ->
            val changeId = change.originPeerId to change.originRevision
            if (journal.containsKey(changeId)) {
                return@forEach
            }
            journal[changeId] = change
            accepted += 1
            lamportVersion = maxOf(lamportVersion, change.lamportVersion)
            advanceKnownRevision(change.originPeerId)
            val existing = records[change.recordId]
            if (existing != null && change.versionStamp <= existing.versionStamp) {
                return@forEach
            }
            when (change.type) {
                SubscriptionChangeType.UPSERT -> {
                    if (subscriptions.put(change.recordId, requireNotNull(change.subscription)) == null) {
                        added += 1
                    }
                }

                SubscriptionChangeType.DELETE -> {
                    if (subscriptions.remove(change.recordId) != null) {
                        removed += 1
                    }
                }
            }
            records[change.recordId] = change
        }
        return SubscriptionApplyResult(accepted, added, removed)
    }

    override fun clearPeerKnowledge() {
        peerKnowledge.clear()
    }

    private fun recordLocalChange(
        serviceId: Int,
        url: String,
        type: SubscriptionChangeType,
        subscription: SyncedSubscription?
    ) {
        val recordId = recordId(serviceId, url)
        val existing = records[recordId]
        if (
            existing != null &&
            existing.type == type &&
            type == SubscriptionChangeType.UPSERT &&
            existing.subscription == subscription
        ) {
            return
        }
        localRevision += 1
        lamportVersion = maxOf(
            lamportVersion,
            existing?.lamportVersion ?: 0
        ) + 1
        val change = SubscriptionChange(
            originPeerId = localPeerId,
            originRevision = localRevision,
            lamportVersion = lamportVersion,
            recordId = recordId,
            serviceId = serviceId,
            url = url,
            type = type,
            subscription = subscription
        )
        journal[localPeerId to localRevision] = change
        knownRevisions[localPeerId] = localRevision
        records[recordId] = change
    }

    private fun advanceKnownRevision(originPeerId: String) {
        var revision = knownRevisions[originPeerId] ?: 0
        while (journal.containsKey(originPeerId to revision + 1)) {
            revision += 1
        }
        knownRevisions[originPeerId] = revision
    }

    private fun recordId(serviceId: Int, url: String): String {
        return SubscriptionRecordId.from(serviceId, url)
    }
}
