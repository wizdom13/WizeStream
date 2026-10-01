package org.schabi.newpipe.local.feed

import androidx.test.filters.SdkSuppress
import java.time.OffsetDateTime
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.ChannelTabHelper

class YoutubeFeedBackfillLiveDiagnosticTest {
    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun inspectAlmurtadVideosTabContinuations() {
        val service = NewPipe.getService(ServiceList.YouTube.serviceId)
        val channelUrl = "https://www.youtube.com/channel/UCMRVzPpyDEr-yvV3KltkPKw"
        val channelInfo = ChannelInfo.getInfo(service, channelUrl)
        val videosTab = channelInfo.tabs.firstOrNull {
            ChannelTabHelper.getTabName(it) == ChannelTabs.VIDEOS
        } ?: error("Videos tab not found; tabs=" + channelInfo.tabs.map(ChannelTabHelper::getTabName))

        val diagnostics = mutableListOf<String>()
        var pageIndex = 0
        var page: ListExtractor.InfoItemsPage<InfoItem>? = null

        val initial = ChannelTabInfo.getInfo(service, videosTab)
        diagnostics += summarizePage(
            pageIndex,
            initial.relatedItems,
            initial.nextPage != null
        )
        page = ListExtractor.InfoItemsPage(
            initial.relatedItems,
            initial.nextPage,
            initial.errors
        )

        while (page?.hasNextPage() == true && pageIndex < 4) {
            pageIndex++
            page = ChannelTabInfo.getMoreItems(service, videosTab, page!!.nextPage)
            diagnostics += summarizePage(
                pageIndex,
                page.items,
                page.hasNextPage()
            )
        }

        throw AssertionError("FEED_DIAG " + diagnostics.joinToString(" | "))
    }

    private fun summarizePage(
        index: Int,
        items: List<InfoItem>,
        hasNextPage: Boolean
    ): String {
        val streams = items.filterIsInstance<StreamInfoItem>()
        val dated = streams.count { it.uploadDate != null }
        val undated = streams.size - dated
        val oldest = streams.mapNotNull { it.uploadDate?.offsetDateTime() }.minOrNull()
        val newest = streams.mapNotNull { it.uploadDate?.offsetDateTime() }.maxOrNull()
        val samples = streams.take(3).joinToString(";") {
            "${it.name.take(30)}@${it.textualUploadDate ?: "-"}"
        }
        return "page=$index items=${items.size} streams=${streams.size} dated=$dated " +
            "undated=$undated next=$hasNextPage oldest=${oldest ?: "-"} " +
            "newest=${newest ?: "-"} samples=[$samples]"
    }
}
