package org.wisso.wizestream.desktop.backend

import java.nio.file.Files
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ScheduledThreadPoolExecutor
import org.schabi.newpipe.sync.AutomaticSyncPolicy
import org.schabi.newpipe.sync.DesktopNetworkEligibility
import org.schabi.newpipe.sync.DesktopSyncLogRepository
import org.schabi.newpipe.sync.DesktopSyncScheduler
import org.schabi.newpipe.sync.SyncRunOutcome
import org.schabi.newpipe.sync.SyncRunTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopAutomaticSyncPersistenceTest {
    @Test
    fun `overdue startup schedules a jittered catch up without moving the regular deadline`() {
        val directory = Files.createTempDirectory("wizestream-sync-scheduler")
        DesktopDatabase(directory).use { database ->
            seedTrustedPeer(database.connection())
            database.openConnection().use { connection ->
                val repository = DesktopSyncLogRepository(connection)
                repository.savePolicy(AutomaticSyncPolicy(
                    enabled = true,
                    intervalMinutes = 60,
                    categories = listOf("subscriptions"),
                    peerIds = listOf("peer-a"),
                    updatedAtEpochMillis = 1
                ))
                repository.updateSchedule(900_000, null)
                val executor = ScheduledThreadPoolExecutor(1)
                val scheduler = DesktopSyncScheduler(
                    repository,
                    object : DesktopNetworkEligibility() {},
                    { _, _, _ -> error("catch-up must remain delayed during this test") },
                    Clock.fixed(Instant.ofEpochMilli(1_000_000), ZoneOffset.UTC),
                    executor,
                    { 30_000 }
                )
                scheduler.start()
                assertEquals(900_000L, repository.scheduleState()["nextRunAtEpochMillis"])
                assertEquals(1_030_000L, repository.scheduleState()["nextWakeAtEpochMillis"])
                scheduler.stop()
            }
        }
    }

    @Test
    fun `schema version three policy logs and retry state persist`() {
        val directory = Files.createTempDirectory("wizestream-automatic-sync")
        DesktopDatabase(directory).use { database ->
            seedTrustedPeer(database.connection())
            database.openConnection().use { syncConnection ->
                val repository = DesktopSyncLogRepository(syncConnection)
                val policy = AutomaticSyncPolicy(
                    enabled = true,
                    intervalMinutes = 30,
                    categories = listOf("subscriptions"),
                    peerIds = listOf("peer-a"),
                    updatedAtEpochMillis = 100
                )
                repository.savePolicy(policy)
                repository.updatePeerRetry("peer-a", succeeded = false, attemptedAt = 1_000)
                repository.recordRun(
                    "run-a", SyncRunTrigger.AUTOMATIC, 1_000, 2_000,
                    SyncRunOutcome.FAILED, policy.categories, policy.peerIds,
                    linkedMapOf(
                        "succeeded" to 0, "failed" to 1,
                        "peers" to listOf(linkedMapOf(
                            "peerId" to "peer-a", "deviceName" to "Phone",
                            "results" to emptyMap<String, Any>(), "error" to "unreachable"
                        ))
                    ),
                    null
                )

                assertEquals(policy, repository.policy())
                assertNotNull(repository.peerRetryState("peer-a")?.get("nextRetryAtEpochMillis"))
                assertEquals("failed", repository.recentRuns(1).single()["outcome"])
            }
            assertEquals("3", schemaVersion(database.connection()))
        }

        DesktopDatabase(directory).use { reopened ->
            reopened.openConnection().use { connection ->
                val repository = DesktopSyncLogRepository(connection)
                assertTrue(repository.policy().enabled)
                assertEquals("run-a", repository.recentRuns(1).single()["runId"])
            }
        }
    }

    @Test
    fun `version two migration preserves synchronization journal rows`() {
        val directory = Files.createTempDirectory("wizestream-sync-migration")
        val path = directory.resolve("wizestream-desktop.db")
        DesktopDatabase(directory).use { database ->
            database.connection().createStatement().use { statement ->
                statement.executeUpdate("UPDATE schema_metadata SET value='2' WHERE key='schema_version'")
                statement.executeUpdate(
                    """INSERT INTO sync_changes(namespace, origin_peer_id, origin_revision,
                        lamport_version, record_id, record_type, change_type)
                        VALUES ('subscriptions', 'phone', 1, 1, 'channel', 'subscription', 'DELETE')"""
                )
                statement.executeUpdate("DROP TABLE sync_peer_run_log")
                statement.executeUpdate("DROP TABLE sync_run_log")
                statement.executeUpdate("DROP TABLE sync_peer_retry_state")
                statement.executeUpdate("DROP TABLE sync_schedule_state")
                statement.executeUpdate("DROP TABLE sync_policy")
            }
        }

        DesktopDatabase(directory).use { migrated ->
            assertEquals("3", schemaVersion(migrated.connection()))
            migrated.openConnection().use { connection ->
                assertEquals(60, DesktopSyncLogRepository(connection).policy().intervalMinutes)
            }
            migrated.connection().createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM sync_changes").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(1, rows.getInt(1))
                }
            }
        }
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            assertEquals("3", schemaVersion(connection))
        }
    }

    private fun seedTrustedPeer(connection: java.sql.Connection) {
        connection.prepareStatement(
            """INSERT INTO trusted_peers(peer_id, public_key, device_name, addresses_json,
                paired_at) VALUES ('peer-a', 'key', 'Phone', '[]', 1)"""
        ).use { it.executeUpdate() }
    }

    private fun schemaVersion(connection: java.sql.Connection): String =
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT value FROM schema_metadata WHERE key='schema_version'"
            ).use { rows ->
                check(rows.next())
                rows.getString(1)
            }
        }
}
