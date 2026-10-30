package org.schabi.newpipe.extractor.services.soundcloud.extractors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import org.schabi.newpipe.extractor.services.soundcloud.linkHandler.SoundcloudChartsLinkHandlerFactory

class SoundcloudChartPagesTest {
    private fun extractor() = SoundcloudChartsExtractor(
        ServiceList.SoundCloud,
        SoundcloudChartsLinkHandlerFactory().fromId("New & hot"),
        "New & hot"
    )

    @Test
    fun playlistIdsContinueAcrossFreshKioskExtractorsAndPreserveOrder() {
        val urls = mutableListOf<String>()
        val downloader = Mockito.mock(Downloader::class.java, Mockito.CALLS_REAL_METHODS)
        Mockito.`when`(downloader.execute(Mockito.any(Request::class.java))).thenAnswer { call ->
            val url = call.getArgument<Request>(0).url()
            urls += url
            val ids = url.substringAfter("&ids=").split(",")
            val body = ids.reversed().joinToString(",", "[", "]") { id ->
                """{"id":$id,"title":"Track $id","permalink_url":"https://soundcloud.com/test/track-$id","duration":1000,"created_at":"2026-01-01T00:00:00Z","artwork_url":"https://example.org/art.jpg","user":{"username":"Test","permalink_url":"https://soundcloud.com/test"}}"""
            }
            Response(200, "OK", emptyMap(), body, body.toByteArray(), url)
        }
        Mockito.mockStatic(NewPipe::class.java, Mockito.CALLS_REAL_METHODS).use { newPipe ->
            newPipe.`when`<Downloader> { NewPipe.getDownloader() }.thenReturn(downloader)
            Mockito.mockStatic(SoundcloudParsingHelper::class.java).use { helper ->
                helper.`when`<String> { SoundcloudParsingHelper.clientId() }.thenReturn("test")
                helper.`when`<java.time.OffsetDateTime> {
                    SoundcloudParsingHelper.parseDateFrom("2026-01-01T00:00:00Z")
                }.thenCallRealMethod()
                val first = extractor().getPage(Page((1..17).map(Int::toString)))
                assertEquals((1..15).map { "Track $it" }, first.items.map { it.name })
                assertEquals(listOf("16", "17"), first.nextPage.ids)
                val last = extractor().getPage(first.nextPage)
                assertEquals(listOf("Track 16", "Track 17"), last.items.map { it.name })
                assertFalse(Page.isValid(last.nextPage))
                assertEquals(2, urls.size)
                assertTrue(first.errors.isEmpty())
                assertTrue(last.errors.isEmpty())
            }
        }
    }

    @Test
    fun legacyUrlPagesStillUseChartsResponses() {
        val downloader = Mockito.mock(Downloader::class.java, Mockito.CALLS_REAL_METHODS)
        val url = "https://api-v2.soundcloud.com/charts?client_id=test"
        val body = """{"collection":[{"track":{"id":1,"title":"Chart track","permalink_url":"https://soundcloud.com/test/track-1","duration":1000,"created_at":"2026-01-01T00:00:00Z","artwork_url":"https://example.org/art.jpg","user":{"username":"Test","permalink_url":"https://soundcloud.com/test"}}}],"next_href":"https://api-v2.soundcloud.com/charts?client_id=test&offset=20"}"""
        Mockito.`when`(downloader.execute(Mockito.any(Request::class.java))).thenReturn(
            Response(200, "OK", emptyMap(), body, body.toByteArray(), url)
        )
        Mockito.mockStatic(NewPipe::class.java, Mockito.CALLS_REAL_METHODS).use { newPipe ->
            newPipe.`when`<Downloader> { NewPipe.getDownloader() }.thenReturn(downloader)
            val result = extractor().getPage(Page(url))
            assertEquals(listOf("Chart track"), result.items.map { it.name })
            assertTrue(result.errors.isEmpty())
            assertEquals("$url&offset=20", result.nextPage.url)
        }
        assertThrows(IllegalArgumentException::class.java) { extractor().getPage(Page(emptyList<String>())) }
    }
}
