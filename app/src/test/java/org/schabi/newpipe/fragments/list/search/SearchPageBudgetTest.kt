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

    @Test
    fun sharesPageAndResultLimitsAcrossMonthlyQueries() {
        val budget = SearchPageBudget(3, 60)
        assertTrue(budget.requestFirst("January", 0))
        budget.completed()
        assertTrue(budget.requestForQuery("January", Page("next"), 20))
        budget.completed()
        assertFalse(budget.requestForQuery("January", Page("next"), 40))
        assertTrue(budget.requestForQuery("February", Page("next"), 40))
        budget.completed()
        assertFalse(budget.requestFirst("March", 40))
        assertFalse(SearchPageBudget(250, 60).requestFirst("March", 60))
    }

    @Test
    fun configurableResultLimitAllowsLargerCollection() {
        assertTrue(SearchPageBudget(250, 20000).request(Page("next"), 5000))
        assertFalse(SearchPageBudget(250, 20000).request(Page("next"), 20000))
        assertFalse(SearchPageBudget(250, 25).requestFirst("January", 25))
    }
}
