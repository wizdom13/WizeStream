package org.schabi.newpipe.extractor.services.odysee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException

class OdyseeLinkHandlerTest {
    @Test
    fun acceptsVideoAndChannelUrls() {
        assertTrue(
            OdyseeStreamLinkHandlerFactory.INSTANCE.onAcceptUrl(
                "https://odysee.com/@Veritasium:f/video-title:a"
            )
        )
        assertTrue(
            OdyseeChannelLinkHandlerFactory.INSTANCE.onAcceptUrl(
                "https://odysee.com/@Veritasium:f"
            )
        )
        assertFalse(
            OdyseeStreamLinkHandlerFactory.INSTANCE.onAcceptUrl(
                "https://odysee.com/@Veritasium:f"
            )
        )
    }

    @Test
    fun convertsBetweenWebAndLbryUris() {
        val web = "https://odysee.com/@channel:abc/video:def"
        val lbry = OdyseeApi.lbryUriFromWebUrl(web)
        assertEquals("lbry://@channel#abc/video#def", lbry)
        assertEquals(web, OdyseeApi.webUrl(lbry))
    }

    @Test
    fun reportedSearchQueriesAreNotRoutedToAnyStreamingService() {
        for (query in listOf("Astraway", "Astrum", "songs", "diy")) {
            assertEquals(query, StreamingService.LinkType.NONE, ServiceList.Odysee.getLinkTypeByUrl(query))
            // This is the URL-detection call used by SearchFragment before submitting a search.
            assertThrows(query, ExtractionException::class.java) { NewPipe.getServiceByUrl(query) }
        }
    }

    @Test
    fun rejectsSearchTextRelativePathsAndUnrelatedUrls() {
        val urls = listOf(
            "Astrum",
            "@Astrum",
            "science videos",
            "/video-title",
            "odysee.com/video-title",
            "//odysee.com/video-title",
            "https://example.invalid/video-title",
            "https://example.invalid/@channel",
            "https://odysee.com.example.invalid/video-title",
            "https://odysee.com@example.invalid/video-title",
            "https://user@odysee.com/video-title",
            "ftp://odysee.com/video-title",
            "https://odysee.com/",
            "https://www.odysee.com/?q=Astrum"
        )
        for (url in urls) {
            assertFalse(url, OdyseeStreamLinkHandlerFactory.INSTANCE.acceptUrl(url))
            assertFalse(url, OdyseeChannelLinkHandlerFactory.INSTANCE.acceptUrl(url))
        }
    }

    @Test
    fun keepsGenuineOdyseeLinksAndTheirClaimIds() {
        for (origin in listOf("https://odysee.com", "http://odysee.com", "https://www.odysee.com", "HTTPS://ODYSEE.COM")) {
            val videoUrl = "$origin/@channel:abc/video:def?t=12#comments"
            assertEquals(ServiceList.Odysee, NewPipe.getServiceByUrl(videoUrl))
            assertEquals(StreamingService.LinkType.STREAM, ServiceList.Odysee.getLinkTypeByUrl(videoUrl))
            assertEquals("lbry://@channel#abc/video#def", OdyseeStreamLinkHandlerFactory.INSTANCE.getId(videoUrl))

            val channelUrl = "$origin/@channel:abc/"
            assertEquals(ServiceList.Odysee, NewPipe.getServiceByUrl(channelUrl))
            assertEquals(StreamingService.LinkType.CHANNEL, ServiceList.Odysee.getLinkTypeByUrl(channelUrl))
            assertEquals("lbry://@channel#abc", OdyseeChannelLinkHandlerFactory.INSTANCE.getId(channelUrl))
        }
        assertEquals("lbry://video#def", OdyseeStreamLinkHandlerFactory.INSTANCE.getId("https://odysee.com/video:def"))
    }

    @Test
    fun keepsPastedYoutubeLinksRoutedToYoutube() {
        assertEquals(ServiceList.YouTube, NewPipe.getServiceByUrl("https://www.youtube.com/watch?v=abcdefghijk"))
        assertEquals(ServiceList.YouTube, NewPipe.getServiceByUrl("https://www.youtube.com/@Astrum"))
    }
}
