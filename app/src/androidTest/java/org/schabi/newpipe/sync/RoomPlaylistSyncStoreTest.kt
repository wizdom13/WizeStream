/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class RoomPlaylistSyncStoreTest {
    private lateinit var phoneDatabase: AppDatabase
    private lateinit var tabletDatabase: AppDatabase

    @Before
    fun setUp() {
        phoneDatabase = newDatabase()
        tabletDatabase = newDatabase()
    }

    @After
    fun tearDown() {
        phoneDatabase.close()
        tabletDatabase.close()
    }

    @Test
    fun localAndRemotePlaylistsRoundTripThroughRoomStores() {
        val phoneStore = RoomPlaylistSyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomPlaylistSyncStore(tabletDatabase, newPeerId())
        val phone = PlaylistSyncEngine(phoneStore)
        val tablet = PlaylistSyncEngine(tabletStore)
        val firstStreamId = phoneDatabase.streamDAO().upsert(testStream(FIRST_URL))
        val secondStreamId = phoneDatabase.streamDAO().upsert(testStream(SECOND_URL))
        val playlistId = phoneDatabase.playlistDAO().insert(
            PlaylistEntity(
                name = "Room playlist",
                isThumbnailPermanent = false,
                thumbnailStreamId = firstStreamId,
                displayIndex = 0
            )
        )
        phoneDatabase.playlistStreamDAO().insertAll(
            listOf(
                PlaylistStreamEntity(playlistId, firstStreamId, 0),
                PlaylistStreamEntity(playlistId, firstStreamId, 1),
                PlaylistStreamEntity(playlistId, secondStreamId, 2)
            )
        )
        tabletDatabase.playlistRemoteDAO().insert(
            PlaylistRemoteEntity(
                serviceId = 0,
                orderingName = "Remote",
                url = REMOTE_URL,
                thumbnailUrl = null,
                uploader = "Uploader",
                displayIndex = 0,
                streamCount = 2
            )
        )

        synchronize(phone, phoneStore, tablet, tabletStore)

        val tabletPlaylist = tabletDatabase.playlistDAO().getAllDirect().single()
        assertEquals("Room playlist", tabletPlaylist.name)
        assertEquals(
            listOf(FIRST_URL, FIRST_URL, SECOND_URL),
            tabletDatabase.playlistStreamDAO()
                .getOrderedStreamsDirect(tabletPlaylist.uid)
                .map(StreamEntity::url)
        )
        assertEquals(
            setOf(REMOTE_URL),
            phoneDatabase.playlistRemoteDAO()
                .getAllDirect()
                .mapNotNull(PlaylistRemoteEntity::url)
                .toSet()
        )

        tabletDatabase.playlistDAO().deletePlaylist(tabletPlaylist.uid)
        synchronize(tablet, tabletStore, phone, phoneStore)

        assertTrue(phoneDatabase.playlistDAO().getAllDirect().isEmpty())
        assertTrue(tabletDatabase.playlistDAO().getAllDirect().isEmpty())
    }

    private fun synchronize(
        initiator: PlaylistSyncEngine,
        initiatorStore: PlaylistSyncStore,
        responder: PlaylistSyncEngine,
        responderStore: PlaylistSyncStore
    ) {
        while (true) {
            val request = initiator.createRequest(responderStore.localPeerId)
            val response = responder.handleRequest(
                initiatorStore.localPeerId,
                request
            )
            initiator.handleResponse(responderStore.localPeerId, response)
            if (!request.hasMore && !response.hasMore) {
                return
            }
        }
    }

    private fun newDatabase(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries()
            .build()
    }

    private fun newPeerId(): String {
        val privateKey = generateKeyPair(KeyType.ED25519).first
        return DeviceIdentity(privateKey).peerId.toBase58()
    }

    private fun testStream(url: String) = StreamEntity(
        serviceId = 0,
        url = url,
        title = url,
        streamType = StreamType.VIDEO_STREAM,
        duration = 60,
        uploader = "Uploader"
    )

    companion object {
        private const val FIRST_URL = "https://example.com/watch/first"
        private const val SECOND_URL = "https://example.com/watch/second"
        private const val REMOTE_URL = "https://example.com/playlist/remote"
    }
}
