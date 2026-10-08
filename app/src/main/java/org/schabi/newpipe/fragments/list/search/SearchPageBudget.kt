package org.schabi.newpipe.fragments.list.search

import org.schabi.newpipe.extractor.Page

/** Bounds background collection and rejects repeated continuation tokens. */
class SearchPageBudget(val maximumPages: Int) {
    var completedPages = 0
        private set
    private val requested = mutableSetOf<String>()

    init {
        require(maximumPages in 1..250)
    }

    fun request(page: Page?, collectedItems: Int): Boolean {
        if (!Page.isValid(page) || completedPages >= maximumPages || collectedItems >= 5000) return false
        val token = listOf(page!!.url, page.id, page.ids, page.cookies?.toSortedMap(), page.body?.contentHashCode()).toString()
        return requested.add(token)
    }

    fun completed() {
        completedPages++
    }
}
