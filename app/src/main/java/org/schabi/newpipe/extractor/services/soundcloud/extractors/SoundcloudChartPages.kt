package org.schabi.newpipe.extractor.services.soundcloud.extractors

import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector

/** Continuations carry their own format, including after the kiosk extractor is recreated. */
object SoundcloudChartPages {
    @JvmStatic
    fun load(service: StreamingService, linkHandler: ListLinkHandler, page: Page?): InfoItemsPage<StreamInfoItem> {
        require(Page.isValid(page)) { "Page contains neither a URL nor track IDs" }
        if (!page!!.ids.isNullOrEmpty()) {
            // Playlist getPage only needs IDs; do not resolve or fetch the playlist again.
            return SoundcloudPlaylistExtractor(service, linkHandler).getPage(page)
        }
        require(!page.url.isNullOrEmpty()) { "Page doesn't contain a URL" }
        val collector = StreamInfoItemsCollector(service.serviceId)
        val nextUrl = SoundcloudParsingHelper.getStreamsFromApi(collector, page.url, true)
        return InfoItemsPage(collector, Page(nextUrl))
    }
}
