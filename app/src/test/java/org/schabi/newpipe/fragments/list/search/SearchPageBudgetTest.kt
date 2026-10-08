package org.schabi.newpipe.fragments.list.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.Page

class SearchPageBudgetTest {
    @Test
    fun stopsAtPageLimitAndAllowsAnotherBatchToResume() {
        val budget = SearchPageBudget(2)
        assertTrue(budget.request(Page("one"), 20))
        budget.completed()
        assertTrue(budget.request(Page("two"), 40))
        budget.completed()
        assertFalse(budget.request(Page("three"), 60))
        assertTrue(SearchPageBudget(2).request(Page("three"), 60))
    }

    @Test
    fun stopsAtResultLimitAndEndOfPages() {
        assertFalse(SearchPageBudget(25).request(Page("next"), 5000))
        assertFalse(SearchPageBudget(25).request(null, 20))
        assertFalse(SearchPageBudget(25).request(Page(""), 20))
    }

    @Test
    fun catchesRepeatedTokensIncludingPostBody() {
        val budget = SearchPageBudget(25)
        assertTrue(budget.request(Page("url", byteArrayOf(1)), 0))
        budget.completed()
        assertTrue(budget.request(Page("url", byteArrayOf(2)), 20))
        budget.completed()
        assertFalse(budget.request(Page("url", byteArrayOf(1)), 40))
    }
}
