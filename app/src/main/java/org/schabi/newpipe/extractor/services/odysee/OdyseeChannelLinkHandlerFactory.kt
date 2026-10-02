package org.schabi.newpipe.extractor.services.odysee

import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.search.filter.FilterItem

class OdyseeChannelLinkHandlerFactory private constructor() : ListLinkHandlerFactory() {
    override fun getId(url: String): String {
        val uri = OdyseeApi.lbryUriFromWebUrl(url)
        val first = uri.substringAfter("lbry://").substringBefore("/")
        require(first.startsWith("@")) { "Not an Odysee channel URL" }
        return "lbry://$first"
    }

    override fun getUrl(
        id: String,
        contentFilter: List<FilterItem>,
        sortFilter: List<FilterItem>?
    ): String = OdyseeApi.webUrl(id)

    override fun onAcceptUrl(url: String): Boolean = runCatching {
        val path = OdyseeApi.lbryUriFromWebUrl(url).substringAfter("lbry://")
        path.startsWith("@") && !path.contains('/')
    }.getOrDefault(false)

    companion object {
        @JvmField
        val INSTANCE = OdyseeChannelLinkHandlerFactory()
    }
}
