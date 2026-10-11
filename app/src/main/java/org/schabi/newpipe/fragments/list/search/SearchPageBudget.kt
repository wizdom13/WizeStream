package org.schabi.newpipe.fragments.list.search

import org.schabi.newpipe.extractor.Page

/** Bounds background collection and rejects repeated continuation tokens. */
class SearchPageBudget @JvmOverloads constructor(val maximumPages: Int, val maximumResults: Int = 5000) {
    companion object {
        const val MAX_PAGES = 250
    }

    var completedPages = 0
        private set
    private val requested = mutableSetOf<String>()

    init {
        require(maximumPages in 1..MAX_PAGES)
        require(maximumResults in 1..20000)
    }

    fun request(page: Page?, collectedItems: Int): Boolean = requestForQuery("", page, collectedItems)

    fun requestForQuery(query: String, page: Page?, collectedItems: Int): Boolean {
        if (!Page.isValid(page) || completedPages >= maximumPages || collectedItems >= maximumResults) return false
        val token = listOf(page!!.url, page.id, page.ids, page.cookies?.toSortedMap(), page.body?.contentHashCode()).toString()
        return requested.add("$query:$token")
    }

    fun requestFirst(query: String, collectedItems: Int): Boolean = completedPages < maximumPages &&
        collectedItems < maximumResults && requested.add("first:$query")

    fun completed() {
        completedPages++
    }
}
