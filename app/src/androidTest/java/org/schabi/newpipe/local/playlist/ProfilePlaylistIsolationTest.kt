package org.schabi.newpipe.local.playlist

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.SavedSearchFeedManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class ProfilePlaylistIsolationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = TestDatabase.createReplacingNewPipeDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun localPlaylistsCannotBeReadOrMutatedAcrossProfiles() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val managerA = LocalPlaylistManager(database, profileA)
        val managerB = LocalPlaylistManager(database, profileB)
        val stream = stream("shared")

        managerA.createPlaylist("Work", listOf(stream)).blockingGet()
        managerB.createPlaylist("Personal", listOf(stream)).blockingGet()

        val playlistA = database.playlistDAO().getAllDirectForProfile(profileA).single()
        val playlistB = database.playlistDAO().getAllDirectForProfile(profileB).single()

        assertEquals("Work", playlistA.name)
        assertEquals("Personal", playlistB.name)
        assertTrue(managerA.getPlaylists().blockingFirst().all { it.uid == playlistA.uid })
        assertTrue(managerB.getPlaylists().blockingFirst().all { it.uid == playlistB.uid })

        managerA.appendToPlaylist(playlistB.uid, listOf(stream("foreign")))
            .test()
            .awaitDone(5, TimeUnit.SECONDS)
            .assertComplete()
            .assertNoValues()
        assertEquals(
            1,
            database.playlistStreamDAO()
                .getOrderedStreamsDirectForProfile(profileB, playlistB.uid)
                .size
        )

        managerA.updatePlaylists(emptyList(), listOf(playlistB.uid)).blockingAwait()
        assertNotNull(
            database.playlistDAO().getPlaylistDirectForProfile(profileB, playlistB.uid)
        )

        managerA.updatePlaylists(emptyList(), listOf(playlistA.uid)).blockingAwait()
        assertNull(database.playlistDAO().getPlaylistDirectForProfile(profileA, playlistA.uid))
        assertNotNull(
            database.playlistDAO().getPlaylistDirectForProfile(profileB, playlistB.uid)
        )
    }

    @Test
    fun sameRemotePlaylistCanBeBookmarkedIndependently() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val url = "https://example.com/playlist"
        val dao = database.playlistRemoteDAO()
        val idA = dao.upsertForProfile(profileA, remotePlaylist(url, profileA))
        val idB = dao.upsertForProfile(profileB, remotePlaylist(url, profileB))
        val managerA = RemotePlaylistManager(database, profileA)
        val managerB = RemotePlaylistManager(database, profileB)

        assertEquals(idA, managerA.getPlaylist(SERVICE_ID, url).blockingFirst().single().uid)
        assertEquals(idB, managerB.getPlaylist(SERVICE_ID, url).blockingFirst().single().uid)

        assertEquals(0, managerA.deletePlaylist(idB).blockingGet())
        assertNotNull(dao.getAllDirectForProfile(profileB).singleOrNull())

        assertEquals(1, managerA.deletePlaylist(idA).blockingGet())
        assertTrue(dao.getAllDirectForProfile(profileA).isEmpty())
        assertEquals(1, dao.getAllDirectForProfile(profileB).size)
    }

    @Test
    fun sameSavedSearchCanExistAndDeleteIndependently() {
        val profileA = "profile-a"
        val profileB = "profile-b"
        val managerA = SavedSearchFeedManager(context, profileA)
        val managerB = SavedSearchFeedManager(context, profileB)

        val idA = managerA.create(
            "Work search",
            SERVICE_ID,
            "privacy",
            emptyArray(),
            intArrayOf()
        ).blockingGet()
        val idB = managerB.create(
            "Personal search",
            SERVICE_ID,
            "privacy",
            emptyArray(),
            intArrayOf()
        ).blockingGet()

        assertEquals(idA, managerA.getAll().blockingGet().single().uid)
        assertEquals(idB, managerB.getAll().blockingGet().single().uid)

        managerA.delete(idB).blockingAwait()
        assertEquals(1, managerB.getAll().blockingGet().size)

        managerA.delete(idA).blockingAwait()
        assertTrue(managerA.getAll().blockingGet().isEmpty())
        assertEquals(1, managerB.getAll().blockingGet().size)
    }

    private fun stream(suffix: String) = StreamEntity(
        serviceId = SERVICE_ID,
        url = "https://example.com/video/$suffix",
        title = suffix,
        streamType = StreamType.VIDEO_STREAM,
        duration = 60,
        uploader = "Channel"
    )

    private fun remotePlaylist(
        url: String,
        profileId: String
    ) = PlaylistRemoteEntity(
        serviceId = SERVICE_ID,
        orderingName = "Playlist",
        url = url,
        thumbnailUrl = null,
        uploader = "Uploader",
        displayIndex = 0,
        streamCount = 1,
        profileId = profileId
    )

    companion object {
        private const val SERVICE_ID = 0
    }
}
