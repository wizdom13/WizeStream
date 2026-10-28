package org.schabi.newpipe.extractor.services.youtube

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeContentDisclosure

class YoutubeContentDisclosureTest {
    @Test
    fun readsProviderDisclosureFromStructuredDescription() {
        val response = JsonParser.`object`().from(
            """{"engagementPanels":[{"engagementPanelSectionListRenderer":{"content":{
              "structuredDescriptionContentRenderer":{"items":[{"howThisWasMadeSectionViewModel":{
                "bodyHeader":{"content":"Made with AI"},
                "bodyText":{"content":"Sounds or visuals were altered or fully generated. Learn more"}
              }}]}
            }}}]}"""
        )
        val existing = MetaInfo()
        val result = YoutubeContentDisclosure.appendTo(listOf(existing), response)
        assertEquals(2, result.size)
        assertEquals("Made with AI", result[1].title)
        assertTrue(result[1] is YoutubeContentDisclosure)
        assertEquals(listOf(existing), YoutubeContentDisclosure.otherMetadata(result))
    }

    @Test
    fun neverInfersDisclosureFromTitleDescriptionOrRecommendedVideos() {
        val response = JsonParser.`object`().from(
            """{"videoDetails":{"title":"Made with AI","shortDescription":"How this was made"},
              "contents":{"howThisWasMadeSectionViewModel":{"bodyHeader":{"content":"Made with AI"}}}}"""
        )
        assertTrue(YoutubeContentDisclosure.appendTo(emptyList(), response).isEmpty())
        assertTrue(YoutubeContentDisclosure.appendTo(emptyList(), null).isEmpty())
    }
}
