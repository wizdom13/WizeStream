/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistSyncEngineTest {
    @Test
    fun `local and remote playlists synchronize with duplicate occurrences`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = PlaylistSyncEngine(phoneStore)
        val tablet = PlaylistSyncEngine(tabletStore)
        val playlistId = phoneStore.createLocalPlaylist(
            "Duplicates",
            listOf(FIRST_URL, FIRST_URL, SECOND_URL)
        )
        tabletStore.bookmarkRemotePlaylist(0, REMOTE_URL, "Remote")

        synchronize(phone, phoneStore, tablet, tabletStore)

        assertEquals(
            listOf(FIRST_URL, FIRST_URL, SECOND_URL),
            tabletStore.playlistUrls(playlistId)
        )
        assertEquals(setOf(REMOTE_URL), phoneStore.remotePlaylistUrls)
        assertEquals(phoneStore.remotePlaylistUrls, tabletStore.remotePlaylistUrls)

        val repeatRounds = synchronize(phone, phoneStore, tablet, tabletStore)
        assertEquals(1, repeatRounds)
        assertTrue(phone.createRequest(tabletStore.localPeerId).changes.isEmpty())
    }

    @Test
    fun `playlist deletion propagates as tombstones`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = PlaylistSyncEngine(phoneStore)
        val tablet = PlaylistSyncEngine(tabletStore)
        val playlistId = phoneStore.createLocalPlaylist("Delete me", listOf(FIRST_URL))
        phoneStore.bookmarkRemotePlaylist(0, REMOTE_URL, "Remote")
        synchronize(phone, phoneStore, tablet, tabletStore)

        tabletStore.deleteLocalPlaylist(playlistId)
        tabletStore.deleteRemotePlaylist(0, REMOTE_URL)
        synchronize(tablet, tabletStore, phone, phoneStore)

        assertFalse(phoneStore.hasLocalPlaylist(playlistId))
        assertFalse(tabletStore.hasLocalPlaylist(playlistId))
        assertTrue(phoneStore.remotePlaylistUrls.isEmpty())
        assertTrue(tabletStore.remotePlaylistUrls.isEmpty())
    }

    @Test
    fun `concurrent metadata ordering and item edits converge`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = PlaylistSyncEngine(phoneStore)
        val tablet = PlaylistSyncEngine(tabletStore)
        val playlistId = phoneStore.createLocalPlaylist(
            "Shared",
            listOf(FIRST_URL, SECOND_URL)
        )
        synchronize(phone, phoneStore, tablet, tabletStore)

        phoneStore.renameLocalPlaylist(playlistId, "Phone name")
        phoneStore.addLocalItem(playlistId, PHONE_URL)
        tabletStore.renameLocalPlaylist(playlistId, "Tablet name")
        tabletStore.addLocalItem(playlistId, TABLET_URL)
        tabletStore.reorderLocalPlaylist(
            playlistId,
            tabletStore.playlistItemIds(playlistId).reversed()
        )

        synchronize(phone, phoneStore, tablet, tabletStore)

        assertEquals(phoneStore.playlistName(playlistId), tabletStore.playlistName(playlistId))
        assertEquals(phoneStore.playlistUrls(playlistId), tabletStore.playlistUrls(playlistId))
        assertEquals(
            setOf(FIRST_URL, SECOND_URL, PHONE_URL, TABLET_URL),
            phoneStore.playlistUrls(playlistId).toSet()
        )
    }

    @Test
    fun `more than one playlist batch is exchanged in one manual sync`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val phone = PlaylistSyncEngine(phoneStore)
        val tablet = PlaylistSyncEngine(tabletStore)
        repeat(MAX_PLAYLIST_CHANGES_PER_BATCH + 9) { index ->
            phoneStore.bookmarkRemotePlaylist(
                0,
                "https://example.com/playlist/$index",
                "Playlist $index"
            )
        }

        val rounds = synchronize(phone, phoneStore, tablet, tabletStore)

        assertTrue(rounds >= 2)
        assertEquals(phoneStore.remotePlaylistUrls, tabletStore.remotePlaylistUrls)
    }

    @Test
    fun `malformed remote playlist identity is rejected`() {
        val phoneStore = newStore()
        val tabletStore = newStore()
        val tablet = PlaylistSyncEngine(tabletStore)
        val malformed = PlaylistChange(
            originPeerId = phoneStore.localPeerId,
            originRevision = 1,
            lamportVersion = 1,
            recordId = "0".repeat(64),
            recordType = PlaylistRecordType.REMOTE_PLAYLIST,
            type = PlaylistChangeType.UPSERT,
            record = SyncedPlaylistRecord(
                remotePlaylist = SyncedRemotePlaylist(
                    serviceId = 0,
                    url = REMOTE_URL,
                    name = "Remote",
                    thumbnailUrl = null,
                    uploader = null,
                    displayIndex = 0,
                    streamCount = 1
                )
            )
        )

        val response = tablet.handleRequest(
            phoneStore.localPeerId,
            PlaylistSyncRequest(
                knownRevisions = emptyMap(),
                changes = listOf(malformed),
                hasMore = false
            )
        )

        assertFalse(response.accepted)
        assertTrue(tabletStore.remotePlaylistUrls.isEmpty())
    }

    private fun synchronize(
        initiator: PlaylistSyncEngine,
        initiatorStore: TestPlaylistSyncStore,
        responder: PlaylistSyncEngine,
        responderStore: TestPlaylistSyncStore
    ): Int {
        var rounds = 0
        while (true) {
            rounds += 1
            val request = initiator.createRequest(responderStore.localPeerId)
            val response = responder.handleRequest(
                initiatorStore.localPeerId,
                request
            )
            initiator.handleResponse(responderStore.localPeerId, response)
            if (!request.hasMore && !response.hasMore) {
                return rounds
            }
        }
    }

    private fun newStore() = TestPlaylistSyncStore(newPeerId())

    private fun newPeerId(): String {
        val privateKey = generateKeyPair(KeyType.ED25519).first
        return DeviceIdentity(privateKey).peerId.toBase58()
    }

    companion object {
        private const val FIRST_URL = "https://example.com/watch/first"
        private const val SECOND_URL = "https://example.com/watch/second"
        private const val PHONE_URL = "https://example.com/watch/phone"
        private const val TABLET_URL = "https://example.com/watch/tablet"
        private const val REMOTE_URL = "https://example.com/playlist/remote"
    }
}
