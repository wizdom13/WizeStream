package org.schabi.newpipe.extractor.services.odysee

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector

class OdyseeChannelExtractor(
    service: StreamingService,
    linkHandler: ListLinkHandler
) : ChannelExtractor(service, linkHandler) {
    private lateinit var channel: JsonObject

    override fun onFetchPage(downloader: org.schabi.newpipe.extractor.downloader.Downloader) {
        channel = OdyseeApi.resolveClaim(linkHandler.id)
    }

    override fun getName(): String {
        return channel.getObject("value").getString("title", channel.getString("name", ""))
    }

    override fun getDescription(): String {
        return channel.getObject("value").getString("description", "")
    }

    override fun getSubscriberCount(): Long = -1L

    override fun getAvatars(): List<Image> {
        val url = channel.getObject("value").getObject("thumbnail").getString("url", "")
        return if (url.isBlank()) {
            emptyList()
        } else {
            listOf(
                Image(
                    url,
                    Image.HEIGHT_UNKNOWN,
                    Image.WIDTH_UNKNOWN,
                    Image.ResolutionLevel.UNKNOWN
                )
            )
        }
    }

    override fun getInitialPage(): InfoItemsPage<StreamInfoItem> = loadPage(1)

    override fun getPage(page: Page): InfoItemsPage<StreamInfoItem> {
        val pageNumber = page.id?.toIntOrNull()?.takeIf { it > 0 }
            ?: throw ParsingException("Invalid Odysee channel continuation")
        return loadPage(pageNumber)
    }

    override fun getTabs(): List<ListLinkHandler> = listOf(
        OdyseeChannelTabLinkHandlerFactory.INSTANCE.fromQuery(
            linkHandler.id,
            listOf(OdyseeChannelTabLinkHandlerFactory.VIDEOS),
            emptyList()
        )
    )

    private fun loadPage(page: Int): InfoItemsPage<StreamInfoItem> {
        if (!::channel.isInitialized) channel = OdyseeApi.resolveClaim(linkHandler.id)
        val result = OdyseeApi.rpc(
            "claim_search",
            JsonObject().apply {
                put("channel_ids", listOf(channel.getString("claim_id")))
                put("claim_type", listOf("stream"))
                put("stream_types", listOf("video"))
                put("has_source", true)
                put("fee_amount", "<=0")
                put("order_by", listOf("release_time"))
                put("page", page)
                put("page_size", OdyseeConstants.PAGE_SIZE)
                put("no_totals", true)
            }
        ).getObject("result")
        val collector = StreamInfoItemsCollector(serviceId)
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
