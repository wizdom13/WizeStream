package org.schabi.newpipe.local.playlist

import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.testUtil.TrampolineSchedulerRule

class RemotePlaylistManagerTest {

    private lateinit var manager: RemotePlaylistManager
    private lateinit var database: AppDatabase

    @get:Rule
    val trampolineScheduler = TrampolineSchedulerRule()

    @Before
    fun setup() {
        database = TestDatabase.createReplacingNewPipeDatabase()
        manager = RemotePlaylistManager(database)
    }

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun lookupByServiceAndUrlDoesNotRequireLoadedPlaylistInfo() {
        val url = "https://www.youtube.com/playlist?list=large"
        val uid = database.playlistRemoteDAO().upsert(remotePlaylist(url, streamCount = 50_000))

        manager.getPlaylist(SERVICE_ID, url)
            .test()
            .awaitCount(1)
            .assertValue { playlists ->
                playlists.size == 1 && playlists.single().uid == uid &&
                    playlists.single().streamCount == 50_000L
            }
    }

    @Test
    fun deleteByStoredUidRefreshesLookupAndIsIdempotent() {
        val url = "https://www.youtube.com/playlist?list=remove"
        val uid = database.playlistRemoteDAO().upsert(remotePlaylist(url))
        val lookup = manager.getPlaylist(SERVICE_ID, url).test().awaitCount(1)

        manager.deletePlaylist(uid).test().await().assertValue(1)
        lookup.awaitCount(2).assertValueAt(1) { it.isEmpty() }
        manager.deletePlaylist(uid).test().await().assertValue(0)
    }

    private fun remotePlaylist(
        url: String,
        streamCount: Long = 1
    ) = PlaylistRemoteEntity(
        serviceId = SERVICE_ID,
        orderingName = "Large playlist",
        url = url,
        thumbnailUrl = null,
        uploader = "Uploader",
        streamCount = streamCount
    )

    companion object {
        private const val SERVICE_ID = 0
    }
}
