package org.schabi.newpipe.profiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.SavedSearchFeedManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class ProfileDeletionIsolationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private var profileA: String? = null
    private var profileB: String? = null

    @Before
    fun setUp() {
        database = TestDatabase.createReplacingNewPipeDatabase()
    }

    @After
    fun tearDown() {
        profileA?.let { ProfileManager.deleteProfile(context, it) }
        profileB?.let { ProfileManager.deleteProfile(context, it) }
        ProfileManager.setActiveProfile(context, ProfileManager.DEFAULT_PROFILE_ID)
        database.close()
    }

    @Test
    fun deletingOneProfilePreservesTheOtherProfilesOwnedData() {
        val suffix = UUID.randomUUID().toString().take(8)
        val first = requireNotNull(
            ProfileManager.createProfile(
                context,
                "Delete $suffix",
                "profile deletion isolation",
                ProfileIcon.WORK.key
            )
        )
        val second = requireNotNull(
            ProfileManager.createProfile(
                context,
                "Keep $suffix",
                "profile deletion isolation",
                ProfileIcon.STUDY.key
            )
        )
        profileA = first.id
        profileB = second.id

        val sharedStream = StreamEntity(
            serviceId = SERVICE_ID,
            url = SHARED_VIDEO_URL,
            title = "Shared video",
            streamType = StreamType.VIDEO_STREAM,
            duration = 120,
            uploader = "Channel"
        )
        LocalPlaylistManager(database, first.id)
            .createPlaylist("Work playlist", listOf(sharedStream))
            .blockingGet()
        LocalPlaylistManager(database, second.id)
            .createPlaylist("Study playlist", listOf(sharedStream))
            .blockingGet()
        val streamId = requireNotNull(
            database.streamDAO().getStreamDirect(SERVICE_ID, SHARED_VIDEO_URL)
        ).uid

        val subscriptionA = SubscriptionEntity(
            serviceId = SERVICE_ID,
            url = SHARED_CHANNEL_URL,
            name = "Work channel",
            profileId = first.id
        ).also { it.uid = database.subscriptionDAO().insert(it) }
        val subscriptionB = SubscriptionEntity(
            serviceId = SERVICE_ID,
            url = SHARED_CHANNEL_URL,
            name = "Study channel",
            profileId = second.id
        ).also { it.uid = database.subscriptionDAO().insert(it) }

        val groupA = database.feedGroupDAO().insert(
            FeedGroupEntity(
                uid = 0,
                name = "Work",
                icon = FeedGroupIcon.WORK,
                profileId = first.id
            )
        )
        val groupB = database.feedGroupDAO().insert(
            FeedGroupEntity(
                uid = 0,
                name = "Study",
                icon = FeedGroupIcon.STAR,
                profileId = second.id
            )
        )
        database.feedGroupDAO().updateSubscriptionsForGroupForProfile(
            first.id,
            groupA,
            listOf(subscriptionA.uid)
        )
        database.feedGroupDAO().updateSubscriptionsForGroupForProfile(
            second.id,
            groupB,
            listOf(subscriptionB.uid)
        )

        database.streamHistoryDAO().insert(
            StreamHistoryEntity(
                streamId,
                OffsetDateTime.parse("2026-09-21T08:00:00Z"),
                1,
                first.id
            )
        )
        database.streamHistoryDAO().insert(
            StreamHistoryEntity(
                streamId,
                OffsetDateTime.parse("2026-09-21T09:00:00Z"),
                2,
                second.id
            )
        )
        database.streamStateDAO().insert(StreamStateEntity(streamId, 10_000, first.id))
        database.streamStateDAO().insert(StreamStateEntity(streamId, 20_000, second.id))

        database.playlistRemoteDAO().upsertForProfile(
            first.id,
            remotePlaylist(first.id)
        )
        database.playlistRemoteDAO().upsertForProfile(
            second.id,
            remotePlaylist(second.id)
        )

        SavedSearchFeedManager(context, first.id)
            .create("Work search", SERVICE_ID, "privacy", emptyArray(), intArrayOf())
            .blockingGet()
        SavedSearchFeedManager(context, second.id)
            .create("Study search", SERVICE_ID, "privacy", emptyArray(), intArrayOf())
            .blockingGet()

        ProfileDeletionManager.deleteProfile(context, first.id).blockingAwait()

        assertNull(ProfileManager.getProfile(context, first.id))
        assertNotNull(ProfileManager.getProfile(context, second.id))

        assertTrue(database.subscriptionDAO().getAllDirectForProfile(first.id).isEmpty())
        assertTrue(database.feedGroupDAO().getAllDirectForProfile(first.id).isEmpty())
        assertTrue(database.playlistDAO().getAllDirectForProfile(first.id).isEmpty())
        assertTrue(database.playlistRemoteDAO().getAllDirectForProfile(first.id).isEmpty())
        assertTrue(database.savedSearchFeedDAO().getAllDirectForProfile(first.id).isEmpty())
        assertTrue(
            database.streamStateDAO()
                .getStateForProfile(first.id, streamId)
                .blockingFirst()
                .isEmpty()
        )
        assertNull(
            database.streamHistoryDAO().getLatestEntryForProfile(first.id, streamId)
        )

        assertEquals(1, database.subscriptionDAO().getAllDirectForProfile(second.id).size)
        assertEquals(1, database.feedGroupDAO().getAllDirectForProfile(second.id).size)
        assertEquals(
            listOf(subscriptionB.uid),
            database.feedGroupDAO().getSubscriptionIdsForDirectForProfile(second.id, groupB)
        )
        assertEquals(1, database.playlistDAO().getAllDirectForProfile(second.id).size)
        assertEquals(1, database.playlistRemoteDAO().getAllDirectForProfile(second.id).size)
        assertEquals(1, database.savedSearchFeedDAO().getAllDirectForProfile(second.id).size)
        assertEquals(
            20_000L,
            database.streamStateDAO()
                .getStateForProfile(second.id, streamId)
                .blockingFirst()
                .single()
                .progressMillis
        )
        assertEquals(
            2L,
            database.streamHistoryDAO()
                .getLatestEntryForProfile(second.id, streamId)
                ?.repeatCount
        )
        assertNotNull(database.streamDAO().getStreamDirect(SERVICE_ID, SHARED_VIDEO_URL))
    }

    private fun remotePlaylist(profileId: String) = PlaylistRemoteEntity(
        serviceId = SERVICE_ID,
        orderingName = "Shared bookmark",
        url = SHARED_PLAYLIST_URL,
        thumbnailUrl = null,
        uploader = "Uploader",
        displayIndex = 0,
        streamCount = 1,
        profileId = profileId
    )

    companion object {
        private const val SERVICE_ID = 0
        private const val SHARED_VIDEO_URL = "https://example.com/video/shared"
        private const val SHARED_CHANNEL_URL = "https://example.com/channel/shared"
        private const val SHARED_PLAYLIST_URL = "https://example.com/playlist/shared"
    }
}
