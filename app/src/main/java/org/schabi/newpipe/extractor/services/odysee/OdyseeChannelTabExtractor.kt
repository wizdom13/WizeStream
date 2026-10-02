package org.schabi.newpipe.extractor.services.odysee

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class OdyseeChannelTabExtractor(service: StreamingService, linkHandler: ListLinkHandler) : ChannelTabExtractor(service, linkHandler) {
    private val channel = OdyseeChannelExtractor(service, linkHandler)

    override fun onFetchPage(downloader: Downloader) = channel.fetchPage()

    override fun getInitialPage(): InfoItemsPage<InfoItem> = convert(channel.initialPage)

    // A fresh extractor must be able to load a continuation without an earlier fetchPage call.
    override fun getPage(page: Page): InfoItemsPage<InfoItem> = convert(channel.getPage(page))

    private fun convert(page: InfoItemsPage<StreamInfoItem>): InfoItemsPage<InfoItem> = InfoItemsPage(page.items.map { it as InfoItem }, page.nextPage, page.errors)
}
