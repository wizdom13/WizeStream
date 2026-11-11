package org.schabi.newpipe.extractor.services.youtube.extractors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.exceptions.ExtractionException

class YoutubeChannelInitialPageTest {
    private val extractor = mock(ChannelTabExtractor::class.java)

    @Test
    fun emptyInitialPageKeepsContinuationAndLoadsPastStreams() {
        val next = Page("https://www.youtube.com/youtubei/v1/browse", "past-streams".toByteArray())
        val initial = InfoItemsPage<InfoItem>(emptyList(), next, emptyList())
        assertTrue(initial.hasNextPage())
        val streams = listOf(mock(InfoItem::class.java))
        val result = InfoItemsPage(streams, null, emptyList())
        `when`(extractor.getPage(next)).thenReturn(result)
        assertSame(result, YoutubeChannelInitialPage.resolve(initial, extractor))
        assertEquals(streams, result.items)
    }

    @Test
    fun genuineEmptyChannelDoesNotRequestAnotherPage() {
        val initial = InfoItemsPage.emptyPage<InfoItem>()
        assertSame(initial, YoutubeChannelInitialPage.resolve(initial, extractor))
        verifyNoInteractions(extractor)
    }

    @Test(expected = ExtractionException::class)
    fun repeatedContinuationFailsInsteadOfLoopingOrShowingNoVideos() {
        val next = Page("https://www.youtube.com/youtubei/v1/browse", "same-token".toByteArray())
        val initial = InfoItemsPage<InfoItem>(emptyList(), next, emptyList())
        `when`(extractor.getPage(next)).thenReturn(initial)
        YoutubeChannelInitialPage.resolve(initial, extractor)
    }

    @Test
    fun extractionErrorsArePreserved() {
        val initial = InfoItemsPage<InfoItem>(emptyList(), Page("next"), listOf(IllegalStateException("bad card")))
        assertSame(initial, YoutubeChannelInitialPage.resolve(initial, extractor))
        verifyNoInteractions(extractor)
    }
}
