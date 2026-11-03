package org.schabi.newpipe.extractor.services.youtube.invidious

import com.grack.nanojson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.Description

class InvidiousMetadataTest {
    private val base = "https://instance.example"
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private var body = ""
    private val requests = mutableListOf<String>()

    @Before
    fun setup() {
        InvidiousBackend.configure(true, base)
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                requests.add(request.url())
                assertTrue(request.url().startsWith("$base/api/v1/"))
                return Response(200, "OK", emptyMap(), body, body.toByteArray(), request.url())
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw UnsupportedOperationException()
        })
    }

    @After
    fun reset() {
        InvidiousBackend.configure(false, "")
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun extractorFallsBackToHtmlAsTextWithoutLoadingEmbeddedResources() {
        body = """{"title":"Video","description":"","descriptionHtml":"First &amp; second<br>Next<p>Paragraph</p><script>bad()</script><img src='https://tracker.example/pixel'><a href='https://example.org'>Link</a>","formatStreams":[{}]}"""
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=BaW_jenozKc")
        extractor.fetchPage()
        val description = extractor.description
        assertEquals(Description.PLAIN_TEXT, description.type)
        assertEquals("First & second\nNext\nParagraph\nLink", description.content)
        assertFalse(description.content.contains("tracker"))
        assertFalse(description.content.contains("bad()"))
        assertEquals(1, requests.size)
    }

    @Test
    fun plainTextTakesPrecedenceAndMissingDescriptionsRemainEmpty() {
        val video = JsonParser.`object`().from("""{"description":"Original\nText <literal>","descriptionHtml":"Other"}""")
        assertEquals("Original\nText <literal>", InvidiousMetadata.description(video).content)
        assertEquals("", InvidiousMetadata.description(JsonParser.`object`().from("{}")).content)
    }

    @Test
    fun singularCommentAvatarIsExtractedForInitialPageAndReplies() {
        InvidiousMediaOrigins.configure(base, "https://companion.example")
        body = """{"comments":[{"author":"User","commentId":"c1","content":"Hello","authorThumbnail":"https://companion.example/ggpht/avatar=s88","replies":{"replyCount":1,"continuation":"next"}}]}"""
        val extractor = ServiceList.YouTube.getCommentsExtractor("https://www.youtube.com/watch?v=BaW_jenozKc")
        extractor.fetchPage()
        val comment = extractor.initialPage.items.single()
        assertEquals("https://companion.example/ggpht/avatar=s88", comment.uploaderAvatarUrl)
        assertEquals(comment.uploaderAvatarUrl, extractor.getPage(comment.replies).items.single().uploaderAvatarUrl)
        assertEquals(2, requests.size)
    }

    @Test
    fun avatarFallbackKeepsArrayCompatibilityAndHostRestrictions() {
        val api = InvidiousApi()
        fun avatar(json: String) = InvidiousMetadata.commentAvatars(JsonParser.`object`().from(json), api)
        assertEquals("$base/ggpht/old", avatar("""{"authorThumbnails":[{"url":"/ggpht/old"}],"authorThumbnail":"/ggpht/new"}""").single().url)
        assertEquals("$base/ggpht/new", avatar("""{"authorThumbnails":[],"authorThumbnail":"https://yt3.ggpht.com/new"}""").single().url)
        assertTrue(avatar("""{"authorThumbnail":"https://untrusted.example/avatar"}""").isEmpty())
        assertTrue(avatar("""{"authorThumbnail":"https://broken host/avatar"}""").isEmpty())
        assertTrue(avatar("{}").isEmpty())
    }
}
