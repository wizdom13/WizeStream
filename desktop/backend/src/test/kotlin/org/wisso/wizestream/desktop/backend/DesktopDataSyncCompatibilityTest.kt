package org.wisso.wizestream.desktop.backend

import java.net.InetAddress
import java.nio.file.Files
import java.sql.Connection
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.schabi.newpipe.sync.DesktopChangeJournal
import org.schabi.newpipe.sync.DesktopHistorySyncStore
import org.schabi.newpipe.sync.DesktopPlaylistSyncStore
import org.schabi.newpipe.sync.DesktopStructuredPreferenceSyncStore
import org.schabi.newpipe.sync.DesktopSubscriptionSyncStore
import org.schabi.newpipe.sync.DesktopSyncStateRepository
import org.schabi.newpipe.sync.HistorySyncCategory
import org.schabi.newpipe.sync.HistorySyncEngine
import org.schabi.newpipe.sync.PlaylistSyncEngine
import org.schabi.newpipe.sync.PortableSettingId
import org.schabi.newpipe.sync.StructuredPreferenceCategory
import org.schabi.newpipe.sync.StructuredPreferenceSyncEngine
import org.schabi.newpipe.sync.SubscriptionSyncEngine
import org.schabi.newpipe.sync.SyncedPortableSetting
import org.schabi.newpipe.sync.SyncedStructuredPreferenceRecord
import org.schabi.newpipe.sync.StructuredPreferenceRecordId
import org.schabi.newpipe.sync.StructuredPreferenceRecordType
import org.schabi.newpipe.sync.subnetCandidates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDataSyncCompatibilityTest {
    @Test
    fun `desktop stores exchange all materialized Phase 2 categories`() {
        database().use { phone ->
            database().use { desktop ->
                seedSubscription(phone.connection())
                seedPlaylist(phone.connection())
                seedLearningNote(phone.connection())
                seedPortableSetting(phone.connection())

                val phoneStores = stores(phone.connection())
                val desktopStores = stores(desktop.connection())

                synchronize(
                    SubscriptionSyncEngine(phoneStores.subscriptions),
                    phoneStores.peerId,
                    SubscriptionSyncEngine(desktopStores.subscriptions),
                    desktopStores.peerId
                )
                synchronize(
                    PlaylistSyncEngine(phoneStores.playlists),
                    phoneStores.peerId,
                    PlaylistSyncEngine(desktopStores.playlists),
                    desktopStores.peerId
                )
                synchronizeHistory(
                    HistorySyncEngine(phoneStores.history),
                    phoneStores.peerId,
                    HistorySyncEngine(desktopStores.history),
                    desktopStores.peerId,
                    HistorySyncCategory.LEARNING_NOTES
                )
                synchronizeStructured(
                    StructuredPreferenceSyncEngine(phoneStores.structured),
                    phoneStores.peerId,
                    StructuredPreferenceSyncEngine(desktopStores.structured),
                    desktopStores.peerId,
                    StructuredPreferenceCategory.SETTINGS
                )

                assertEquals(1, count(desktop.connection(), "subscriptions"))
                assertEquals(1, count(desktop.connection(), "playlists"))
                assertEquals(1, count(desktop.connection(), "playlist_items"))
                assertEquals(1, count(desktop.connection(), "learning_notes"))
                assertEquals(1, count(desktop.connection(), "portable_records"))

                phone.connection().createStatement().use {
                    it.executeUpdate("DELETE FROM subscriptions")
                }
                synchronize(
                    SubscriptionSyncEngine(phoneStores.subscriptions),
                    phoneStores.peerId,
                    SubscriptionSyncEngine(desktopStores.subscriptions),
                    desktopStores.peerId
                )
                assertEquals(0, count(desktop.connection(), "subscriptions"))
                assertTrue(
                    desktopStores.subscriptions.getKnownRevisions().isNotEmpty(),
                    "the deletion tombstone must remain in the synchronization clock"
                )
            }
        }
    }

    @Test
    fun `desktop LAN discovery stays inside the local slash 24`() {
        val local = InetAddress.getByName("192.168.40.17") as java.net.Inet4Address
        val candidates = subnetCandidates(local)
        assertEquals(253, candidates.size)
        assertFalse(local in candidates)
        assertTrue(candidates.all { it.hostAddress.startsWith("192.168.40.") })
    }

    @Test
    fun `completed desktop downloads enter the shared metadata journal`() {
        database().use { desktop ->
            database().use { phone ->
                val desktopStores = stores(desktop.connection())
                val phoneStores = stores(phone.connection())
                val syncId = desktopStores.structured.recordCompletedDownload(
                    "https://media.example/video.mp4",
                    "Fixture video.mp4",
                    "video/mp4",
                    42,
                    1_700_000_000_000,
                    "v"
                )

                synchronizeStructured(
                    StructuredPreferenceSyncEngine(desktopStores.structured),
                    desktopStores.peerId,
                    StructuredPreferenceSyncEngine(phoneStores.structured),
                    phoneStores.peerId,
                    StructuredPreferenceCategory.COMPLETED_DOWNLOADS
                )

                assertEquals(1, count(phone.connection(), "portable_records"))
                phone.connection().prepareStatement(
                    "SELECT record_id, payload_json FROM portable_records WHERE category=?"
                ).use { statement ->
                    statement.setString(1, StructuredPreferenceCategory.COMPLETED_DOWNLOADS.name)
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals(syncId, rows.getString(1))
                        assertTrue(rows.getString(2).contains("Fixture video.mp4"))
                    }
                }
            }
        }
    }

    @Test
    fun `completed download recording is idempotent for a desktop job id`() {
        database().use { desktop ->
            val stores = stores(desktop.connection())
            val jobId = "11111111-1111-4111-8111-111111111111"
            repeat(2) {
                stores.structured.recordCompletedDownload(
                    "https://media.example/video.mp4",
                    "Fixture video.mp4",
                    "video/mp4",
                    42,
                    1_700_000_000_000,
                    "v",
                    jobId
                )
            }
            assertEquals(1, count(desktop.connection(), "portable_records"))
        }
    }

    private fun stores(connection: Connection): Stores {
        val peerId = DesktopSyncStateRepository(connection).loadOrCreateIdentity().peerId.toBase58()
        val journal = DesktopChangeJournal(connection, peerId)
        return Stores(
            peerId,
            DesktopSubscriptionSyncStore(connection, peerId, journal),
            DesktopPlaylistSyncStore(connection, peerId, journal),
            DesktopHistorySyncStore(connection, peerId, journal),
            DesktopStructuredPreferenceSyncStore(connection, peerId, journal)
        )
    }

    private fun synchronize(
        first: SubscriptionSyncEngine,
        firstPeerId: String,
        second: SubscriptionSyncEngine,
        secondPeerId: String
    ) {
        repeat(MAX_ROUNDS) {
            val request = first.createRequest(secondPeerId)
            val response = second.handleRequest(firstPeerId, request)
            first.handleResponse(secondPeerId, response)
            if (!request.hasMore && !response.hasMore) return
        }
        error("subscription fixture exceeded the round limit")
    }

    private fun synchronize(
        first: PlaylistSyncEngine,
        firstPeerId: String,
        second: PlaylistSyncEngine,
        secondPeerId: String
    ) {
        repeat(MAX_ROUNDS) {
            val request = first.createRequest(secondPeerId)
            val response = second.handleRequest(firstPeerId, request)
            first.handleResponse(secondPeerId, response)
            if (!request.hasMore && !response.hasMore) return
        }
        error("playlist fixture exceeded the round limit")
    }

    private fun synchronizeHistory(
        first: HistorySyncEngine,
        firstPeerId: String,
        second: HistorySyncEngine,
        secondPeerId: String,
        category: HistorySyncCategory
    ) {
        repeat(MAX_ROUNDS) {
            val request = first.createRequest(secondPeerId, category)
            val response = second.handleRequest(firstPeerId, request)
            first.handleResponse(secondPeerId, category, response)
            if (!request.hasMore && !response.hasMore) return
        }
        error("history fixture exceeded the round limit")
    }

    private fun synchronizeStructured(
        first: StructuredPreferenceSyncEngine,
        firstPeerId: String,
        second: StructuredPreferenceSyncEngine,
        secondPeerId: String,
        category: StructuredPreferenceCategory
    ) {
        repeat(MAX_ROUNDS) {
            val request = first.createRequest(secondPeerId, category)
            val response = second.handleRequest(firstPeerId, request)
            first.handleResponse(secondPeerId, category, response)
            if (!request.hasMore && !response.hasMore) return
        }
        error("structured preference fixture exceeded the round limit")
    }

    private fun seedSubscription(connection: Connection) {
        connection.prepareStatement(
            """INSERT INTO subscriptions(service_id, url, name, avatar_url, created_at,
                subscriber_count, description, youtube_mode_mask) VALUES (0, ?, ?, NULL, 1, 42, ?, 1)"""
        ).use { statement ->
            statement.setString(1, "https://www.youtube.com/channel/UCdesktopfixture")
            statement.setString(2, "Fixture channel")
            statement.setString(3, "Shared from Android fixture")
            statement.executeUpdate()
        }
    }

    private fun seedPlaylist(connection: Connection) {
        val playlistId = UUID.randomUUID().toString()
        connection.prepareStatement(
            "INSERT INTO playlists(id, name, created_at, display_index) VALUES (?, ?, 1, 0)"
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.setString(2, "Fixture playlist")
            statement.executeUpdate()
        }
        connection.prepareStatement(
            """INSERT INTO playlist_items(playlist_id, position, service_id, url, title,
                duration, item_id, stream_type, uploader) VALUES (?, 0, 0, ?, ?, 60, ?,
                'VIDEO_STREAM', 'Fixture uploader')"""
        ).use { statement ->
            statement.setString(1, playlistId)
            statement.setString(2, "https://www.youtube.com/watch?v=fixture")
            statement.setString(3, "Fixture stream")
            statement.setString(4, UUID.randomUUID().toString())
            statement.executeUpdate()
        }
    }

    private fun seedLearningNote(connection: Connection) {
        connection.prepareStatement(
            """INSERT INTO learning_notes(id, service_id, url, position_seconds, note,
                created_at, updated_at, title, stream_type, duration, uploader)
                VALUES (?, 0, ?, 12, ?, 100, 100, ?, 'VIDEO_STREAM', 60, ?)"""
        ).use { statement ->
            statement.setString(1, UUID.randomUUID().toString())
            statement.setString(2, "https://www.youtube.com/watch?v=learningfixture")
            statement.setString(3, "Remember this")
            statement.setString(4, "Learning fixture")
            statement.setString(5, "Fixture uploader")
            statement.executeUpdate()
        }
    }

    private fun seedPortableSetting(connection: Connection) {
        val record = SyncedStructuredPreferenceRecord(
            portableSetting = SyncedPortableSetting(
                settingId = PortableSettingId.THEME,
                stringValue = "auto"
            )
        )
        connection.prepareStatement(
            """INSERT INTO portable_records(category, record_id, record_type,
                parent_record_id, payload_json) VALUES (?, ?, ?, NULL, ?)"""
        ).use { statement ->
            statement.setString(1, StructuredPreferenceCategory.SETTINGS.name)
            statement.setString(2, StructuredPreferenceRecordId.portableSetting(PortableSettingId.THEME))
            statement.setString(3, StructuredPreferenceRecordType.PORTABLE_SETTING.name)
            statement.setString(4, JSON.encodeToString(record))
            statement.executeUpdate()
        }
    }

    private fun count(connection: Connection, table: String): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun database(): DesktopDatabase = DesktopDatabase(
        Files.createTempDirectory("wizestream-desktop-sync-test")
    )

    private data class Stores(
        val peerId: String,
        val subscriptions: DesktopSubscriptionSyncStore,
        val playlists: DesktopPlaylistSyncStore,
        val history: DesktopHistorySyncStore,
        val structured: DesktopStructuredPreferenceSyncStore
    )

    companion object {
        private const val MAX_ROUNDS = 32
        private val JSON = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}
