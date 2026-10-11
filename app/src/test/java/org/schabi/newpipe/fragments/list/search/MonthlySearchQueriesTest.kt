package org.schabi.newpipe.fragments.list.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlySearchQueriesTest {
    @Test
    fun coversLeapYearAndDecemberWithAdjacentCalendarBoundaries() {
        val queries = MonthlySearchQueries.forYear("lecture", 2024)
        assertEquals(12, queries.size)
        assertEquals("lecture after:2024-02-01 before:2024-03-01", queries[1])
        assertEquals("lecture after:2024-12-01 before:2025-01-01", queries.last())
    }

    @Test
    fun replacesExistingDateOperatorsAndIdentifiesConflictingUploadFilters() {
        val query = MonthlySearchQueries.forYear("lecture after:2020-01-01 BEFORE:2021-01-01", 2024).first()
        assertEquals("lecture after:2024-01-01 before:2024-02-01", query)
        assertTrue(MonthlySearchQueries.isUploadDateFilter("past_month"))
        assertFalse(MonthlySearchQueries.isUploadDateFilter("sort_view_count"))
    }
}
