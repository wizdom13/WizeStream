package org.schabi.newpipe.local.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDirectoryTest {
    private val country = TvDirectoryCountry("LB", "Lebanon", true, "lebanon")
    private val selection = TvDirectorySelection(TvDirectorySource.IPTV_ORG, country)

    @Test
    fun countryFeedsRetainLogosCategoriesAndAlternativeQualityStreamsWithoutWebPlayers() {
        val imported = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="News.lb@SD" tvg-logo="https://example.org/logo.png" group-title="News",News (720p)
            https://one.example/live.m3u8
            #EXTINF:-1 tvg-id="News.lb@HD" group-title="General",News (1080p)
            https://two.example/live.m3u8
            #EXTINF:-1,Website
            https://www.youtube.com/watch?v=example
            #EXTINF:-1,Unsupported
            http://example.org/live
            """.trimIndent()
        )
        val (playlist, skipped) = TvDirectory.prepare(selection, imported, 123L)
        val channel = playlist.channels.single()
        assertEquals(2, skipped)
        assertEquals("News", channel.name)
        assertEquals("news.lb", channel.tvgId)
        assertEquals("LB", channel.country)
        assertEquals("News; General", channel.group)
        assertEquals("https://example.org/logo.png", channel.logo)
        assertEquals(listOf("https://one.example/live.m3u8", "https://two.example/live.m3u8"), channel.urls)
        assertEquals(123L, playlist.updatedAt)
        assertEquals("https://iptv-org.github.io/iptv/countries/lb.m3u", playlist.address)
        assertEquals("https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_lebanon.m3u8", country.address(TvDirectorySource.FREE_TV))
    }

    @Test
    fun duplicatesAcrossDirectoriesMergeButSeparateCountriesAndCustomChannelsRemainDistinct() {
        val first = TvChannel("News", "https://one.example/live", country = "LB", tvgId = "news.lb")
        val second = first.copy(url = "https://two.example/live", tvgId = "", logo = "https://example.org/logo.png")
        val metadataOnly = first.copy(group = "News", logo = second.logo)
        val us = first.copy(country = "US", url = "https://us.example/live", tvgId = "news.us")
        val worldwide = first.copy(country = "US", url = "https://world.example/live")
        val custom = first.copy(country = "", url = "https://custom.example/live")
        val channels = combineTvChannels(listOf(first, metadataOnly, second, first, us, custom, worldwide))
        assertEquals(3, channels.size)
        assertEquals(listOf(first.url, second.url, worldwide.url), channels[0].urls)
        assertEquals(second.logo, channels[0].logo)
        assertEquals("News", channels[0].group)
        assertEquals(us, channels[1])
        assertEquals(custom, channels[2])
    }

    @Test
    fun favoritesSurviveRotatingDirectoryUrlsAndNewImportsReplaceMatchingCustomSources() {
        val channel = TvChannel("News", "https://old.example/live", tvgId = "news.lb", country = "LB")
        val old = TvPlaylist(selection.id, selection.name, selection.address, listOf(channel), 100L)
        val library = TvLibrary(listOf(old), setOf(channel.url))
        val replacement = channel.copy(url = "https://new.example/live", alternatives = listOf("https://other.example/live"))
        val refreshed = library.withPlaylist(old.copy(channels = listOf(replacement), updatedAt = 200L))
        assertEquals(replacement.urls.toSet(), refreshed.favorites)
        assertEquals(200L, refreshed.playlists.single().updatedAt)
        val custom = old.copy(id = "custom-import")
        assertEquals(selection.id, TvLibrary(listOf(custom)).withPlaylist(old).playlists.single().id)
        assertFalse(TvDirectory.stale(old, 101L))
        assertTrue(TvDirectory.stale(old, 100L + TvDirectory.REFRESH_INTERVAL))
        assertTrue(TvDirectory.stale(old, 99L))
    }
}
