package org.schabi.newpipe.local.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaylistStoreTest {
    @Test
    fun profilePlaylistsFavoritesAndExtensionlessHlsSurviveReloadAndDeleteIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = TvPlaylistStore(context, UUID.randomUUID().toString())
        val second = TvPlaylistStore(context, UUID.randomUUID().toString())
        try {
            val channel = TvChannel("Channel", "https://tv.example/play", "News", "https://tv.example/logo.png", true)
            val source = TvPlaylist("source", "My channels", "https://tv.example/list.m3u", listOf(channel))
            val library = TvLibrary(listOf(source), setOf(channel.url))
            first.write(library)
            assertEquals(library, first.read())
            assertTrue(second.read().playlists.isEmpty())
            second.write(library.copy(favorites = emptySet()))
            first.delete()
            assertTrue(first.read().playlists.isEmpty())
            assertEquals(emptySet<String>(), second.read().favorites)
            assertEquals(listOf(source), second.read().playlists)
        } finally {
            first.delete()
            second.delete()
        }
    }
}
