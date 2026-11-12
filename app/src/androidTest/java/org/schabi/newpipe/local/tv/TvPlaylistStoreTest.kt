package org.schabi.newpipe.local.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaylistStoreTest {
    @Test
    fun directoryMetadataAndAlternativesPersistAndOversizedRefreshLeavesTheCacheIntact() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TvPlaylistStore(context, UUID.randomUUID().toString())
        try {
            val channel = TvChannel("News", "https://example.org/main", tvgId = "news", country = "LB", alternatives = listOf("https://example.org/backup"))
            val playlist = TvPlaylist("directory:iptv-org:LB", "Lebanon", "https://iptv-org.github.io/iptv/countries/lb.m3u", listOf(channel), 123L)
            val library = TvLibrary(listOf(playlist), channel.urls.toSet())
            store.write(library)
            assertEquals(library, store.read())
            val tooMany = List(2501) { index -> channel.copy(url = "https://example.org/$index", alternatives = listOf("https://backup.example/$index")) }
            assertThrows(java.io.IOException::class.java) { store.write(library.copy(playlists = listOf(playlist.copy(channels = tooMany)))) }
            assertEquals(library, store.read())
        } finally {
            store.delete()
        }
    }

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
