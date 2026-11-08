package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.schabi.newpipe.extractor.MultiInfoItemsCollector
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo.PlaylistType
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.filter.FilterItem

class YoutubeChannelCoursesTest {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()

    @Before
    fun setup() {
        NewPipe.init(mock(Downloader::class.java))
    }

    @After
    fun reset() {
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun codeEmporiumCoursesWithoutShareUrlsCollectWithoutFeedErrors() {
        assertCourses("code-emporium", "CodeEmporium", 8)
    }

    @Test
    fun digikeyCoursesWithoutShareUrlsCollectWithoutFeedErrors() {
        assertCourses("digikey", "DigiKey", 4)
    }

    @Test
    fun wrappedCourseCardsAndGridPlaylistsUseTheSameMetadata() {
        val renderer = fixture("digikey").getObject(0).getObject("playlistRenderer")
        val wrapped = JsonObject().apply {
            put(
                "itemSectionRenderer",
                JsonObject().apply {
                    put(
                        "contents",
                        JsonArray().apply {
                            add(
                                JsonObject().apply {
                                    put(
                                        "richItemRenderer",
                                        JsonObject().apply {
                                            put("content", JsonObject().apply { put("playlistRenderer", renderer) })
                                        }
                                    )
                                }
                            )
                            add(JsonObject().apply { put("gridPlaylistRenderer", renderer) })
                        }
                    )
                }
            )
        }
        val collector = collect(JsonArray().apply { add(wrapped) }, "DigiKey")
        assertTrue(collector.errors.toString(), collector.errors.isEmpty())
        assertEquals(2, collector.items.size)
        assertEquals(collector.items[0].url, collector.items[1].url)
    }

    @Test
    fun radioCardsKeepTheirWatchUrlAndInfiniteMixCount() {
        val url = "https://www.youtube.com/watch?v=abcdefghijk&list=RDabcdefghijk"
        val radio = JsonParser.`object`().from(
            """{
              "radioRenderer": {
                "shareUrl": "$url",
                "title": {"simpleText": "Mix"},
                "longBylineText": {"simpleText": "YouTube"},
                "videoCountShortText": {"simpleText": "50+"},
                "thumbnail": {"thumbnails": [{"url": "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"}]}
              }
            }"""
        )
        val collector = collect(JsonArray().apply { add(radio) }, "Channel")
        assertTrue(collector.errors.toString(), collector.errors.isEmpty())
        val item = collector.items.single() as PlaylistInfoItem
        assertEquals(url, item.url)
        assertEquals("YouTube", item.uploaderName)
        assertEquals(PlaylistType.MIX_STREAM, item.playlistType)
        assertEquals(org.schabi.newpipe.extractor.ListExtractor.ITEM_COUNT_INFINITE, item.streamCount)
    }

    private fun assertCourses(fixtureName: String, channelName: String, count: Int) {
        val cards = fixture(fixtureName)
        val collector = collect(cards, channelName)
        assertTrue(collector.errors.toString(), collector.errors.isEmpty())
        assertEquals(count, collector.items.size)
        collector.items.forEachIndexed { index, info ->
            val renderer = cards.getObject(index).getObject("playlistRenderer")
            val item = info as PlaylistInfoItem
            assertEquals("https://www.youtube.com/playlist?list=${renderer.getString("playlistId")}", item.url)
            assertEquals(renderer.getObject("title").getString("simpleText"), item.name)
            assertEquals(renderer.getString("videoCount").toLong(), item.streamCount)
            assertEquals(channelName, item.uploaderName)
            assertEquals(PlaylistType.NORMAL, item.playlistType)
            assertTrue(item.thumbnailUrl.startsWith("https://i.ytimg.com/"))
        }
    }

    // Sanitized course cards captured from the two reported channels on 2026-11-08.
    private fun fixture(name: String): JsonArray = javaClass.getResourceAsStream("/youtube/channel-courses/$name.json")!!.use {
        JsonParser.`object`().from(it).getArray("items")
    }

    private fun collect(items: JsonArray, channelName: String): MultiInfoItemsCollector {
        val url = "https://www.youtube.com/channel/UC5_6ZD6s8klmMu9TXEB_1IA/courses"
        val handler = ListLinkHandler(url, url, "channel/UC5_6ZD6s8klmMu9TXEB_1IA", listOf(FilterItem(-1, ChannelTabs.COURSES)), null)
        val extractor = YoutubeChannelTabExtractor(ServiceList.YouTube, handler)
        val collector = MultiInfoItemsCollector(ServiceList.YouTube.serviceId)
        YoutubeChannelTabExtractor::class.java.getDeclaredMethod(
            "collectItemsFrom",
            MultiInfoItemsCollector::class.java,
            JsonArray::class.java,
            List::class.java
        ).apply { isAccessible = true }.invoke(extractor, collector, items, listOf(channelName, url))
        return collector
    }
}
