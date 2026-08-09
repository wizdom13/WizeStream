/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import java.sql.Connection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class DesktopSubscriptionSyncStore(
    private val connection: Connection,
    override val localPeerId: String,
    private val journal: DesktopChangeJournal
) : SubscriptionSyncStore {
    override fun reconcileLocalSubscriptions() {
        val desired = synchronized(connection) {
            connection.prepareStatement(
                """SELECT service_id, url, name, avatar_url, subscriber_count,
                    description, youtube_mode_mask FROM subscriptions ORDER BY service_id, url"""
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            val subscription = SyncedSubscription(
                                serviceId = rows.getInt(1),
                                url = rows.getString(2).trim(),
                                name = rows.getString(3),
                                avatarUrl = rows.getString(4),
                                subscriberCount = rows.getLong(5).takeUnless { rows.wasNull() },
                                description = rows.getString(6),
                                youtubeModeMask = rows.getInt(7)
                            )
                            val recordId = SubscriptionRecordId.from(
                                subscription.serviceId,
                                subscription.url
                            )
                            add(
                                DesktopDesiredRecord(
                                    recordId,
                                    RECORD_TYPE,
                                    payloadJson = JSON.encodeToString(subscription)
                                )
                            )
                        }
                    }
                }
            }
        }
        journal.reconcile(NAMESPACE, desired)
    }

    override fun getKnownRevisions(): Map<String, Long> = journal.knownRevisions(NAMESPACE)

    override fun getPendingChanges(peerId: String, limit: Int): SubscriptionChangeBatch {
        val batch = journal.pending(NAMESPACE, peerId, limit)
        return SubscriptionChangeBatch(batch.changes.map(::toModel), batch.hasMore)
    }

    override fun acknowledgePeer(peerId: String, knownRevisions: Map<String, Long>) {
        SubscriptionSyncValidation.validateKnownRevisions(knownRevisions)
        journal.acknowledge(NAMESPACE, peerId, knownRevisions)
    }

    override fun applyChanges(changes: List<SubscriptionChange>): SubscriptionApplyResult {
        SubscriptionSyncValidation.validateChanges(changes)
        val previous = journal.records(NAMESPACE).associateBy(DesktopJournalRecord::recordId)
        val applied = journal.apply(NAMESPACE, changes.map(::toJournal))
        var added = 0
        var removed = 0
        applied.affectedRecordIds.forEach { recordId ->
            val record = requireNotNull(journal.record(NAMESPACE, recordId))
            val wasDeleted = previous[recordId]?.isDeleted != false
            if (record.isDeleted) {
                materializeDelete(recordId)
                if (!wasDeleted) removed += 1
            } else {
                materializeUpsert(decode(requireNotNull(record.payloadJson)))
                if (wasDeleted) added += 1
            }
        }
        return SubscriptionApplyResult(applied.acceptedChanges, added, removed)
    }

    override fun clearPeerKnowledge() = journal.clearPeerKnowledge()

    private fun materializeUpsert(subscription: SyncedSubscription) {
        synchronized(connection) {
            connection.prepareStatement(
                """INSERT INTO subscriptions(service_id, url, name, avatar_url, created_at,
                    subscriber_count, description, youtube_mode_mask) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(service_id, url) DO UPDATE SET name=excluded.name,
                    avatar_url=excluded.avatar_url, subscriber_count=excluded.subscriber_count,
                    description=excluded.description, youtube_mode_mask=excluded.youtube_mode_mask"""
            ).use { statement ->
                statement.setInt(1, subscription.serviceId)
                statement.setString(2, subscription.url)
                statement.setString(3, subscription.name)
                statement.setString(4, subscription.avatarUrl)
                statement.setLong(5, System.currentTimeMillis())
                statement.setObject(6, subscription.subscriberCount)
                statement.setString(7, subscription.description)
                statement.setInt(8, subscription.youtubeModeMask)
                statement.executeUpdate()
            }
        }
    }

    private fun materializeDelete(recordId: String) {
        val current = journal.record(NAMESPACE, recordId) ?: return
        val subscription = current.payloadJson?.let(::decode) ?: return
        synchronized(connection) {
            connection.prepareStatement("DELETE FROM subscriptions WHERE service_id=? AND url=?")
                .use { statement ->
                    statement.setInt(1, subscription.serviceId)
                    statement.setString(2, subscription.url)
                    statement.executeUpdate()
                }
        }
    }

    private fun toModel(change: DesktopJournalChange): SubscriptionChange {
        val subscription = change.payloadJson?.let(::decode)
        return SubscriptionChange(
            originPeerId = change.originPeerId,
            originRevision = change.originRevision,
            lamportVersion = change.lamportVersion,
            recordId = change.recordId,
            serviceId = subscription?.serviceId
                ?: throw SubscriptionSyncException("Stored subscription data is missing"),
            url = subscription.url,
            type = SubscriptionChangeType.valueOf(change.changeType),
            subscription = subscription.takeIf { change.changeType == "UPSERT" }
        )
    }

    private fun toJournal(change: SubscriptionChange): DesktopJournalChange {
        val payload = change.subscription?.let { JSON.encodeToString(it) }
            ?: journal.record(NAMESPACE, change.recordId)?.payloadJson
            ?: JSON.encodeToString(SyncedSubscription(change.serviceId, change.url))
        return DesktopJournalChange(
            change.originPeerId,
            change.originRevision,
            change.lamportVersion,
            change.recordId,
            RECORD_TYPE,
            null,
            change.type.name,
            payload
        )
    }

    private fun decode(value: String): SyncedSubscription = try {
        JSON.decodeFromString(value)
    } catch (error: Exception) {
        throw SubscriptionSyncException("Stored subscription synchronization data is malformed", error)
    }

    companion object {
        private const val NAMESPACE = "subscriptions"
        private const val RECORD_TYPE = "SUBSCRIPTION"
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}
