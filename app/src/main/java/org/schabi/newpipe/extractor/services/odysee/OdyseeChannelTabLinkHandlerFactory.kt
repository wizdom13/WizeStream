package org.schabi.newpipe.extractor.services.odysee

import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.search.filter.FilterItem

class OdyseeChannelTabLinkHandlerFactory private constructor() : ListLinkHandlerFactory() {
    override fun getId(url: String): String = OdyseeChannelLinkHandlerFactory.INSTANCE.getId(url)

    override fun getUrl(id: String, contentFilter: List<FilterItem>, sortFilter: List<FilterItem>?): String {
        require(contentFilter.isEmpty() || contentFilter.single().name == ChannelTabs.VIDEOS) { "Unsupported Odysee channel tab" }
        return OdyseeApi.webUrl(id)
    }

    override fun onAcceptUrl(url: String): Boolean = OdyseeChannelLinkHandlerFactory.INSTANCE.onAcceptUrl(url)

    companion object {
        @JvmField val INSTANCE = OdyseeChannelTabLinkHandlerFactory()

        @JvmField val VIDEOS = FilterItem(0, ChannelTabs.VIDEOS)
    }
}
