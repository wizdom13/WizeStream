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
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.ChannelTabHelper

class YoutubeFeedBackfillLiveDiagnosticTest {
    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun inspectAlmurtadVideosTabContinuations() {
        NewPipe.setPreferredLocalization(Localization("en", "AE"))
        NewPipe.setPreferredContentCountry(ContentCountry("AE"))
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
        diagnostics += "videos:" + summarizePage(
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
            diagnostics += "videos:" + summarizePage(
                pageIndex,
                page.items,
                page.hasNextPage()
            )
        }

        val liveTab = channelInfo.tabs.firstOrNull {
            ChannelTabHelper.getTabName(it) == ChannelTabs.LIVESTREAMS
        }
        if (liveTab != null) {
            val liveInitial = ChannelTabInfo.getInfo(service, liveTab)
            diagnostics += "live:" + summarizePage(
                0,
                liveInitial.relatedItems,
                liveInitial.nextPage != null
            )
            var livePage = ListExtractor.InfoItemsPage(
                liveInitial.relatedItems,
                liveInitial.nextPage,
                liveInitial.errors
            )
            var livePageIndex = 0
            while (livePage.hasNextPage() && livePageIndex < 3) {
                livePageIndex++
                livePage = ChannelTabInfo.getMoreItems(service, liveTab, livePage.nextPage)
                diagnostics += "live:" + summarizePage(
                    livePageIndex,
                    livePage.items,
                    livePage.hasNextPage()
                )
            }
        } else {
            diagnostics += "live:tab-missing"
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
        val membersOnly = streams.count { it.requiresMembership() }
        val retentionCutoff = OffsetDateTime.now().minusWeeks(13)
        val withinRetention = streams.count {
            it.uploadDate?.offsetDateTime()?.isAfter(retentionCutoff) == true
        }
        val live = streams.count {
            it.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                it.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
        }
        val shorts = streams.count { it.isShortFormContent }
        val oldest = streams.mapNotNull { it.uploadDate?.offsetDateTime() }.minOrNull()
        val newest = streams.mapNotNull { it.uploadDate?.offsetDateTime() }.maxOrNull()
        val samples = streams.take(3).joinToString(";") {
            "${it.name.take(30)}@${it.textualUploadDate ?: "-"}"
        }
        val series = streams.filter { it.name.contains("منتجع الخلافة") }
            .joinToString(";") {
                "${it.name.take(60)}@${it.textualUploadDate ?: "-"}" +
                    "#type=${it.streamType}#duration=${it.duration}" +
                    "#member=${it.requiresMembership()}#url=${it.url}"
            }
        return "page=$index items=${items.size} streams=${streams.size} dated=$dated " +
            "undated=$undated members=$membersOnly within13w=$withinRetention " +
            "live=$live shorts=$shorts " +
            "next=$hasNextPage oldest=${oldest ?: "-"} " +
            "newest=${newest ?: "-"} samples=[$samples] series=[$series]"
    }
}
