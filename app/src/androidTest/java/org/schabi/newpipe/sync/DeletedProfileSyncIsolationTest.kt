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
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class DeletedProfileSyncIsolationTest {
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
    fun staleSubscriptionCannotRecreateDeletedProfileData() {
        phoneDatabase.subscriptionDAO().insert(
            SubscriptionEntity(
                serviceId = SERVICE_ID,
                url = CHANNEL_URL,
                name = "Deleted profile channel",
                profileId = DELETED_PROFILE_ID
            )
        )
        val phoneStore = RoomSubscriptionSyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomSubscriptionSyncStore(
            tabletDatabase,
            newPeerId(),
            canMaterializeProfile = { false }
        )
        val phone = SubscriptionSyncEngine(phoneStore)
        val tablet = SubscriptionSyncEngine(tabletStore)

        val request = phone.createRequest(tabletStore.localPeerId)
        val response = tablet.handleRequest(phoneStore.localPeerId, request)

        assertTrue(response.accepted)
        assertTrue(
            tabletDatabase.subscriptionDAO()
                .getAllDirectForProfile(DELETED_PROFILE_ID)
                .isEmpty()
        )
    }

    @Test
    fun stalePlaylistBookmarkCannotRecreateDeletedProfileData() {
        phoneDatabase.playlistRemoteDAO().upsertForProfile(
            DELETED_PROFILE_ID,
            PlaylistRemoteEntity(
                serviceId = SERVICE_ID,
                orderingName = "Deleted profile playlist",
                url = PLAYLIST_URL,
                thumbnailUrl = null,
                uploader = "Uploader",
                displayIndex = 0,
                streamCount = 1,
                profileId = DELETED_PROFILE_ID
            )
        )
        val phoneStore = RoomPlaylistSyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomPlaylistSyncStore(
            tabletDatabase,
            newPeerId(),
            canMaterializeProfile = { false }
        )
        val phone = PlaylistSyncEngine(phoneStore)
        val tablet = PlaylistSyncEngine(tabletStore)

        val request = phone.createRequest(tabletStore.localPeerId)
        val response = tablet.handleRequest(phoneStore.localPeerId, request)

        assertTrue(response.accepted)
        assertTrue(
            tabletDatabase.playlistRemoteDAO()
                .getAllDirectForProfile(DELETED_PROFILE_ID)
                .isEmpty()
        )
    }

    @Test
    fun staleWatchHistoryAndProgressCannotRecreateDeletedProfileData() {
        val streamId = phoneDatabase.streamDAO().upsert(
            StreamEntity(
                serviceId = SERVICE_ID,
                url = VIDEO_URL,
                title = "Deleted profile video",
                streamType = StreamType.VIDEO_STREAM,
                duration = 120,
                uploader = "Uploader"
            )
        )
        phoneDatabase.streamHistoryDAO().insert(
            StreamHistoryEntity(
                streamUid = streamId,
                accessDate = OffsetDateTime.parse("2026-09-21T10:00:00Z"),
                repeatCount = 2,
                profileId = DELETED_PROFILE_ID
            )
        )
        phoneDatabase.streamStateDAO().upsert(
            StreamStateEntity(
                streamUid = streamId,
                progressMillis = 42_000,
                profileId = DELETED_PROFILE_ID
            )
        )
        val phoneStore = RoomHistorySyncStore(phoneDatabase, newPeerId())
        val tabletStore = RoomHistorySyncStore(
            tabletDatabase,
            newPeerId(),
            canMaterializeProfile = { false }
        )
        val phone = HistorySyncEngine(phoneStore)
        val tablet = HistorySyncEngine(tabletStore)

        val request = phone.createRequest(
            tabletStore.localPeerId,
            HistorySyncCategory.WATCH
        )
        val response = tablet.handleRequest(phoneStore.localPeerId, request)

        assertTrue(response.accepted)
        assertTrue(
            tabletDatabase.streamHistoryDAO()
                .getAllDirectForProfile(DELETED_PROFILE_ID)
                .isEmpty()
        )
        assertTrue(
            tabletDatabase.streamStateDAO()
                .getAllDirectForProfile(DELETED_PROFILE_ID)
                .isEmpty()
        )
    }

    private fun newDatabase(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    private fun newPeerId(): String {
        val privateKey = generateKeyPair(KeyType.ED25519).first
        return DeviceIdentity(privateKey).peerId.toBase58()
    }

    companion object {
        private const val SERVICE_ID = 0
        private const val DELETED_PROFILE_ID = "11111111-1111-1111-1111-111111111111"
        private const val CHANNEL_URL = "https://example.com/channel/deleted"
        private const val PLAYLIST_URL = "https://example.com/playlist/deleted"
        private const val VIDEO_URL = "https://example.com/video/deleted"
    }
}
