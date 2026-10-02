package org.schabi.newpipe.extractor.services.odysee

import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.comments.CommentsExtractor
import org.schabi.newpipe.extractor.kiosk.KioskList
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor

class OdyseeService(id: Int) : StreamingService(
    id,
    "Odysee",
    listOf(ServiceInfo.MediaCapability.VIDEO)
) {
    override fun getBaseUrl(): String = OdyseeConstants.BASE_URL

    override fun getStreamLHFactory(): LinkHandlerFactory = OdyseeStreamLinkHandlerFactory.INSTANCE

    override fun getChannelLHFactory(): ListLinkHandlerFactory = OdyseeChannelLinkHandlerFactory.INSTANCE

    override fun getChannelTabLHFactory(): ListLinkHandlerFactory = OdyseeChannelTabLinkHandlerFactory.INSTANCE

    override fun getPlaylistLHFactory(): ListLinkHandlerFactory? = null

    override fun getSearchQHFactory(): SearchQueryHandlerFactory = OdyseeSearchQueryHandlerFactory.INSTANCE

    override fun getCommentsLHFactory(): ListLinkHandlerFactory? = null

    override fun getSearchExtractor(queryHandler: SearchQueryHandler): SearchExtractor {
        return OdyseeSearchExtractor(this, queryHandler)
    }

    override fun getSuggestionExtractor(): SuggestionExtractor? = null

    override fun getSubscriptionExtractor(): SubscriptionExtractor? = null

    override fun getKioskList(): KioskList = KioskList(this)

    override fun getChannelExtractor(linkHandler: ListLinkHandler): ChannelExtractor = OdyseeChannelExtractor(this, linkHandler)

    override fun getChannelTabExtractor(linkHandler: ListLinkHandler): ChannelTabExtractor = OdyseeChannelTabExtractor(this, linkHandler)

    override fun getPlaylistExtractor(linkHandler: ListLinkHandler): PlaylistExtractor? = null

    override fun getStreamExtractor(linkHandler: LinkHandler): StreamExtractor {
        return OdyseeStreamExtractor(this, linkHandler)
    }

    override fun getCommentsExtractor(linkHandler: ListLinkHandler): CommentsExtractor? = null
}
