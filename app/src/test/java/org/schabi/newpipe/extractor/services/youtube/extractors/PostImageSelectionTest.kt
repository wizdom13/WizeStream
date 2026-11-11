package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.schabi.newpipe.extractor.localization.TimeAgoParser

class PostImageSelectionTest {
    @Test
    fun rendersOnePhotoPerRendererAndKeepsDistinctGalleryPhotos() {
        val renderer = JsonParser.`object`().from(
            """
            {"backstageAttachment":{"postMultiImageRenderer":{"images":[
              {"backstageImageRenderer":{"image":{"thumbnails":[
                {"url":"https://example.com/one-small.jpg","width":100,"height":100},
                {"url":"https://example.com/one-large.jpg","width":1000,"height":1000},
                {"url":"https://example.com/one-medium.jpg","width":500,"height":500}
              ]}}},
              {"backstageImageRenderer":{"image":{"thumbnails":[
                {"url":"https://example.com/two-large.jpg","width":800,"height":800},
                {"url":"https://example.com/two-small.jpg","width":80,"height":80}
              ]}}}
            ]}}}
            """.trimIndent()
        )
        val extractor = YoutubePostInfoItemExtractor(renderer, mock(TimeAgoParser::class.java))
        assertEquals(
            listOf("https://example.com/one-large.jpg", "https://example.com/two-large.jpg"),
            extractor.images.map { it.url }
        )
    }

    @Test
    fun singlePhotoWithSeveralResolutionsAppearsOnceAndEmptyPhotosAreIgnored() {
        val renderer = JsonParser.`object`().from(
            """
            {"backstageAttachment":{"backstageImageRenderer":{"image":{"thumbnails":[
              {"url":"https://example.com/small.jpg","width":100,"height":100},
              {"url":"https://example.com/large.jpg","width":800,"height":800}
            ]}}}}
            """.trimIndent()
        )
        assertEquals(
            listOf("https://example.com/large.jpg"),
            YoutubePostInfoItemExtractor(renderer, mock(TimeAgoParser::class.java)).images.map { it.url }
        )
        assertEquals(emptyList<Any>(), PostImageSelection.singlePhoto(emptyList()))
    }
}
