package org.wisso.wizestream.desktop.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.schabi.newpipe.extractor.ServiceList

class ExtractorFacadeTest {
    @Test
    fun `youtube search retains default content filter`() {
        val facade = ExtractorFacade()
        val handler = facade.createSearchQuery(ServiceList.YouTube, "mazen")

        assertTrue(handler.contentFilters.isNotEmpty())
        assertEquals("all", handler.contentFilters.first().name)
        ServiceList.YouTube.getSearchExtractor(handler)
    }
}
