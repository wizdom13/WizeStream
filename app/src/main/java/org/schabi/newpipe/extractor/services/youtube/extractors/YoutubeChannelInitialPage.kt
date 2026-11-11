package org.schabi.newpipe.extractor.services.youtube.extractors

import java.io.IOException
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.exceptions.ExtractionException

/** YouTube can defer all channel cards to a continuation, even on the first page. */
object YoutubeChannelInitialPage {
    @JvmStatic
    @Throws(IOException::class, ExtractionException::class)
    fun resolve(initial: InfoItemsPage<InfoItem>, extractor: ChannelTabExtractor): InfoItemsPage<InfoItem> {
        var page = initial
        val visited = mutableSetOf<String>()
        repeat(5) {
            if (page.items.isNotEmpty() || page.errors.isNotEmpty() || !page.hasNextPage()) return page
            val next = page.nextPage
            val key = "${next.url}:${next.id}:${next.body?.contentHashCode()}"
            if (!visited.add(key)) throw ExtractionException("Repeated empty YouTube channel continuation")
            page = extractor.getPage(next)
        }
        if (page.items.isEmpty() && page.hasNextPage()) {
            throw ExtractionException("YouTube channel returned too many empty continuation pages")
        }
        return page
    }
}
