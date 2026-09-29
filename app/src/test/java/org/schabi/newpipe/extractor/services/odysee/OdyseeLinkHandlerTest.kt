package org.schabi.newpipe.extractor.services.odysee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OdyseeLinkHandlerTest {
    @Test
    fun acceptsVideoAndChannelUrls() {
        assertTrue(
            OdyseeStreamLinkHandlerFactory.INSTANCE.onAcceptUrl(
                "https://odysee.com/@Veritasium:f/video-title:a"
            )
        )
        assertTrue(
            OdyseeChannelLinkHandlerFactory.INSTANCE.onAcceptUrl(
                "https://odysee.com/@Veritasium:f"
            )
        )
        assertFalse(
            OdyseeStreamLinkHandlerFactory.INSTANCE.onAcceptUrl(
                "https://odysee.com/@Veritasium:f"
            )
        )
    }

    @Test
    fun convertsBetweenWebAndLbryUris() {
        val web = "https://odysee.com/@channel:abc/video:def"
        val lbry = OdyseeApi.lbryUriFromWebUrl(web)
        assertEquals("lbry://@channel#abc/video#def", lbry)
        assertEquals(web, OdyseeApi.webUrl(lbry))
    }
}
