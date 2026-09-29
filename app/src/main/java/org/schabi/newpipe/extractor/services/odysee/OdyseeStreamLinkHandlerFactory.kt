package org.schabi.newpipe.extractor.services.odysee

import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory

class OdyseeStreamLinkHandlerFactory private constructor() : LinkHandlerFactory() {
    override fun getId(url: String): String {
        val uri = OdyseeApi.lbryUriFromWebUrl(url)
        if (uri.substringAfter("lbry://").startsWith("@")) {
            val parts = uri.substringAfter("lbry://").split("/")
            if (parts.size < 2) throw ParsingException("Odysee channel URL is not a stream")
        }
        return uri
    }

    override fun getUrl(id: String): String = OdyseeApi.webUrl(id)

    override fun onAcceptUrl(url: String): Boolean = runCatching {
        val uri = OdyseeApi.lbryUriFromWebUrl(url)
        !uri.substringAfter("lbry://").startsWith("@") ||
            uri.substringAfter("lbry://").contains("/")
    }.getOrDefault(false)

    companion object {
        @JvmField val INSTANCE = OdyseeStreamLinkHandlerFactory()
    }
}
