/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.Connection
import kotlin.math.absoluteValue

internal class DesktopSyncLogRepository(private val connection: Connection) {
    fun policy(): AutomaticSyncPolicy = synchronized(connection) {
        connection.prepareStatement(
            "SELECT enabled, interval_minutes, categories_json, peer_ids_json, updated_at FROM sync_policy WHERE id=1"
        ).use { statement ->
            statement.executeQuery().use { rows ->
                check(rows.next()) { "Automatic synchronization policy is missing" }
                AutomaticSyncPolicy(
                    enabled = rows.getInt(1) != 0,
                    intervalMinutes = rows.getInt(2),
                    categories = JSON.readValue(rows.getString(3), STRING_LIST),
                    peerIds = JSON.readValue(rows.getString(4), STRING_LIST),
                    updatedAtEpochMillis = rows.getLong(5)
                )
            }
        }
    }

    fun savePolicy(policy: AutomaticSyncPolicy) = synchronized(connection) {
        policy.validate()
        connection.prepareStatement(
            """UPDATE sync_policy SET enabled=?, interval_minutes=?, categories_json=?,
                peer_ids_json=?, updated_at=? WHERE id=1"""
        ).use { statement ->
            statement.setInt(1, if (policy.enabled) 1 else 0)
            statement.setInt(2, policy.intervalMinutes)
            statement.setString(3, JSON.writeValueAsString(policy.categories))
            statement.setString(4, JSON.writeValueAsString(policy.peerIds))
            statement.setLong(5, policy.updatedAtEpochMillis)
            check(statement.executeUpdate() == 1)
        }
    }

    fun scheduleState(): Map<String, Any?> = synchronized(connection) {
        connection.prepareStatement(
            "SELECT next_run_at, next_wake_at, last_attempt_at, last_outcome FROM sync_schedule_state WHERE id=1"
        ).use { statement ->
            statement.executeQuery().use { rows ->
                check(rows.next())
                linkedMapOf(
                    "nextRunAtEpochMillis" to rows.nullableLong(1),
                    "nextWakeAtEpochMillis" to rows.nullableLong(2),
                    "lastAttemptAtEpochMillis" to rows.nullableLong(3),
                    "lastOutcome" to rows.getString(4)?.lowercase()
                )
            }
        }
    }

    fun updateSchedule(nextRunAt: Long?, nextWakeAt: Long?, attemptedAt: Long? = null, outcome: SyncRunOutcome? = null) =
        synchronized(connection) {
            connection.prepareStatement(
                """UPDATE sync_schedule_state SET next_run_at=?, next_wake_at=?,
                    last_attempt_at=COALESCE(?, last_attempt_at),
                    last_outcome=COALESCE(?, last_outcome) WHERE id=1"""
            ).use { statement ->
                statement.setObject(1, nextRunAt)
                statement.setObject(2, nextWakeAt)
                statement.setObject(3, attemptedAt)
                statement.setString(4, outcome?.name)
                statement.executeUpdate()
            }
        }

    fun eligiblePeerIds(peerIds: List<String>, now: Long): List<String> = synchronized(connection) {
        peerIds.filter { peerId ->
            connection.prepareStatement(
                "SELECT next_retry_at FROM sync_peer_retry_state WHERE peer_id=?"
            ).use { statement ->
                statement.setString(1, peerId)
                statement.executeQuery().use { rows -> !rows.next() || rows.nullableLong(1)?.let { it <= now } != false }
            }
        }
    }

    fun retryDuePlan(peerIds: List<String>, now: Long): Map<String, List<String>> = synchronized(connection) {
        buildMap {
        peerIds.forEach { peerId ->
            connection.prepareStatement(
                "SELECT next_retry_at, failed_categories_json FROM sync_peer_retry_state WHERE peer_id=?"
            ).use { statement ->
                statement.setString(1, peerId)
                statement.executeQuery().use { rows ->
                    if (rows.next() && rows.nullableLong(1)?.let { it <= now } == true) {
                        put(peerId, JSON.readValue(rows.getString(2), STRING_LIST))
                    }
                }
            }
        }
        }
    }

    fun earliestRetry(peerIds: List<String>): Long? = synchronized(connection) {
        if (peerIds.isEmpty()) return@synchronized null
        peerIds.mapNotNull { peerId ->
            connection.prepareStatement(
                "SELECT next_retry_at FROM sync_peer_retry_state WHERE peer_id=?"
            ).use { statement ->
                statement.setString(1, peerId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.nullableLong(1) else null }
            }
        }.minOrNull()
    }

    fun updatePeerRetry(
        peerId: String,
        succeeded: Boolean,
        attemptedAt: Long,
        failedCategories: List<String> = emptyList()
    ) = synchronized(connection) {
        val previous = connection.prepareStatement(
            "SELECT failure_count FROM sync_peer_retry_state WHERE peer_id=?"
        ).use { statement ->
            statement.setString(1, peerId)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
        }
        val failures = if (succeeded) 0 else previous + 1
        val nextRetry = if (succeeded) null else attemptedAt + retryDelayMillis(peerId, failures)
        connection.prepareStatement(
            """INSERT INTO sync_peer_retry_state(peer_id, failure_count, next_retry_at,
                last_attempt_at, last_outcome, failed_categories_json) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(peer_id) DO UPDATE SET failure_count=excluded.failure_count,
                next_retry_at=excluded.next_retry_at, last_attempt_at=excluded.last_attempt_at,
                last_outcome=excluded.last_outcome,
                failed_categories_json=excluded.failed_categories_json"""
        ).use { statement ->
            statement.setString(1, peerId)
            statement.setInt(2, failures)
            statement.setObject(3, nextRetry)
            statement.setLong(4, attemptedAt)
            statement.setString(5, if (succeeded) SyncRunOutcome.SUCCESS.name else SyncRunOutcome.FAILED.name)
            statement.setString(6, JSON.writeValueAsString(if (succeeded) emptyList() else failedCategories))
            statement.executeUpdate()
        }
    }

    fun peerRetryState(peerId: String): Map<String, Any?>? = synchronized(connection) {
        connection.prepareStatement(
            "SELECT failure_count, next_retry_at, last_attempt_at, last_outcome FROM sync_peer_retry_state WHERE peer_id=?"
        ).use { statement ->
            statement.setString(1, peerId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else linkedMapOf(
                    "consecutiveFailures" to rows.getInt(1),
                    "nextRetryAtEpochMillis" to rows.nullableLong(2),
                    "lastAttemptAtEpochMillis" to rows.nullableLong(3),
                    "lastOutcome" to rows.getString(4)?.lowercase()
                )
            }
        }
    }

    fun recordRun(
        runId: String,
        trigger: SyncRunTrigger,
        startedAt: Long,
        completedAt: Long,
        outcome: SyncRunOutcome,
        categories: List<String>,
        peerIds: List<String>,
        result: Map<String, Any?>?,
        error: String?
    ) = synchronized(connection) {
        val succeeded = (result?.get("succeeded") as? Number)?.toInt() ?: 0
        val failed = (result?.get("failed") as? Number)?.toInt() ?: 0
        connection.prepareStatement(
            """INSERT INTO sync_run_log(run_id, trigger, started_at, completed_at, outcome,
                requested_categories_json, requested_peer_ids_json, succeeded, failed, error)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        ).use { statement ->
            statement.setString(1, runId)
            statement.setString(2, trigger.name)
            statement.setLong(3, startedAt)
            statement.setLong(4, completedAt)
            statement.setString(5, outcome.name)
            statement.setString(6, JSON.writeValueAsString(categories))
            statement.setString(7, JSON.writeValueAsString(peerIds))
            statement.setInt(8, succeeded)
            statement.setInt(9, failed)
            statement.setString(10, error?.take(500))
            statement.executeUpdate()
        }
        @Suppress("UNCHECKED_CAST")
        (result?.get("peers") as? List<Map<String, Any?>>).orEmpty().forEach { peer ->
            val peerError = peer["error"] as? String
            connection.prepareStatement(
                """INSERT INTO sync_peer_run_log(run_id, peer_id, device_name, outcome, error,
                    details_json) VALUES (?, ?, ?, ?, ?, ?)"""
            ).use { statement ->
                statement.setString(1, runId)
                statement.setString(2, peer["peerId"] as String)
                statement.setString(3, peer["deviceName"] as String)
                statement.setString(4, if (peerError == null) SyncRunOutcome.SUCCESS.name else SyncRunOutcome.FAILED.name)
                statement.setString(5, peerError?.take(500))
                statement.setString(6, JSON.writeValueAsString(peer["results"] ?: emptyMap<String, Any>()))
                statement.executeUpdate()
            }
        }
        purgeOldRuns(completedAt)
    }

    fun recentRuns(limit: Int): List<Map<String, Any?>> = synchronized(connection) {
        require(limit in 1..100) { "Run log limit must be between 1 and 100" }
        connection.prepareStatement(
            """SELECT run_id, trigger, started_at, completed_at, outcome,
                requested_categories_json, requested_peer_ids_json, succeeded, failed, error
                FROM sync_run_log ORDER BY started_at DESC LIMIT ?"""
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val runId = rows.getString(1)
                        add(linkedMapOf(
                            "runId" to runId,
                            "trigger" to rows.getString(2).lowercase(),
                            "startedAtEpochMillis" to rows.getLong(3),
                            "completedAtEpochMillis" to rows.nullableLong(4),
                            "outcome" to rows.getString(5).lowercase(),
                            "requestedCategories" to JSON.readValue(rows.getString(6), STRING_LIST),
                            "requestedPeerIds" to JSON.readValue(rows.getString(7), STRING_LIST),
                            "succeeded" to rows.getInt(8),
                            "failed" to rows.getInt(9),
                            "error" to rows.getString(10),
                            "peers" to peerRuns(runId)
                        ))
                    }
                }
            }
        }
    }

    private fun peerRuns(runId: String): List<Map<String, Any?>> = connection.prepareStatement(
        "SELECT peer_id, device_name, outcome, error, details_json FROM sync_peer_run_log WHERE run_id=? ORDER BY lower(device_name)"
    ).use { statement ->
        statement.setString(1, runId)
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) add(linkedMapOf(
                    "peerId" to rows.getString(1), "deviceName" to rows.getString(2),
                    "outcome" to rows.getString(3).lowercase(), "error" to rows.getString(4),
                    "results" to JSON.readValue(rows.getString(5), MAP)
                ))
            }
        }
    }

    private fun purgeOldRuns(now: Long) {
        connection.prepareStatement(
            """DELETE FROM sync_run_log WHERE started_at < ? OR run_id NOT IN
                (SELECT run_id FROM sync_run_log ORDER BY started_at DESC LIMIT 100)"""
        ).use { statement ->
            statement.setLong(1, now - 30L * 24 * 60 * 60 * 1_000)
            statement.executeUpdate()
        }
    }

    companion object {
        private val JSON = ObjectMapper()
        private val STRING_LIST = object : TypeReference<List<String>>() {}
        private val MAP = object : TypeReference<Map<String, Any?>>() {}
        private val RETRY_MINUTES = longArrayOf(5, 15, 30, 60, 180, 360)

        internal fun retryDelayMillis(peerId: String, failureCount: Int): Long {
            val base = RETRY_MINUTES[(failureCount - 1).coerceIn(RETRY_MINUTES.indices)] * 60_000
            val jitterPercent = (peerId.hashCode().toLong().absoluteValue % 21).toInt()
            return base + base * jitterPercent / 100
        }
    }
}

private fun java.sql.ResultSet.nullableLong(index: Int): Long? =
    getLong(index).takeUnless { wasNull() }
