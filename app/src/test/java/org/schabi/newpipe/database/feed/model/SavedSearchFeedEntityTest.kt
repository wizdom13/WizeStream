package org.schabi.newpipe.database.feed.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedSearchFeedEntityTest {
    @Test
    fun filtersRoundTripWithoutLosingOrder() {
        val contentFilters = arrayOf("videos", "live")
        val sortFilters = intArrayOf(4, 7, 11)
        val entity = SavedSearchFeedEntity(
            name = "Android news",
            serviceId = 0,
            query = "Android",
            contentFilter = SavedSearchFeedEntity.encodeContentFilters(contentFilters),
            sortFilter = SavedSearchFeedEntity.encodeSortFilters(sortFilters)
        )

        assertArrayEquals(contentFilters, entity.contentFilters())
        assertArrayEquals(sortFilters, entity.sortFilters())
    }

    @Test
    fun blankFiltersRemainEmpty() {
        val entity = SavedSearchFeedEntity(
            name = "Privacy",
            serviceId = 0,
            query = "privacy"
        )

        assertEquals(0, entity.contentFilters().size)
        assertEquals(0, entity.sortFilters().size)
    }
}
