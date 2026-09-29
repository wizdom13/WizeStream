package org.schabi.newpipe.extractor.services.odysee

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.search.filter.FilterItem

class OdyseeSearchQueryHandlerFactory private constructor() : SearchQueryHandlerFactory() {
    override fun getUrl(
        query: String,
        selectedContentFilter: List<FilterItem>,
        selectedSortFilter: List<FilterItem>?
    ): String = OdyseeConstants.SEARCH_API + "?s=" +
        URLEncoder.encode(query, StandardCharsets.UTF_8) +
        "&size=" + OdyseeConstants.PAGE_SIZE + "&claimType=file&nsfw=false&free_only=true"

    override fun getSearchString(url: String): String {
        val query = java.net.URI(url).rawQuery.orEmpty()
        return query.split("&").firstOrNull { it.startsWith("s=") }
            ?.substringAfter("s=")
            ?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8) }
            .orEmpty()
    }

    companion object {
        @JvmField val INSTANCE = OdyseeSearchQueryHandlerFactory()
    }
}
