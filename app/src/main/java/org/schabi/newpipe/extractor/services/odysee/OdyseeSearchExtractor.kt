package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MultiInfoItemsCollector
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchExtractor

class OdyseeSearchExtractor(
    service: StreamingService,
    linkHandler: SearchQueryHandler
) : SearchExtractor(service, linkHandler) {
    override fun onFetchPage(
        downloader: org.schabi.newpipe.extractor.downloader.Downloader
    ) = Unit

    override fun getSearchSuggestion(): String = ""

    override fun isCorrectedSearch(): Boolean = false

    override fun getInitialPageInternal(): InfoItemsPage<InfoItem> = loadPage(1)

    override fun getPageInternal(page: Page): InfoItemsPage<InfoItem> {
        return loadPage(page.id?.toIntOrNull() ?: 1)
    }

    private fun loadPage(page: Int): InfoItemsPage<InfoItem> {
        val result = OdyseeApi.rpc(
            "claim_search",
            JsonObject().apply {
                put("text", linkHandler.id)
                put("claim_type", listOf("stream"))
                put("stream_types", listOf("video"))
                put("has_source", true)
                put("fee_amount", "<=0")
                put("page", page)
                put("page_size", OdyseeConstants.PAGE_SIZE)
                put("no_totals", true)
            }
        ).getObject("result")

        val collector = MultiInfoItemsCollector(serviceId)
        val items = result.getArray("items")
        items.forEach { value ->
            if (value is JsonObject) collector.commit(OdyseeStreamInfoItemExtractor(value))
        }
        val next = if (items.size >= OdyseeConstants.PAGE_SIZE) {
            Page(url, (page + 1).toString())
        } else {
            null
        }
        return InfoItemsPage(collector, next)
    }
}
