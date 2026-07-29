package org.schabi.newpipe.extractor.services.youtube;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeShortsLinkHandlerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeShortsKioskTest {
    @Test
    void advertisesShortsAsAYoutubeKiosk() throws Exception {
        final KioskList kiosks = ServiceList.YouTube.getKioskList();

        assertTrue(kiosks.getAvailableKiosks().contains(
                YoutubeShortsLinkHandlerFactory.KIOSK_ID));
    }

    @Test
    void shortsLinkHandlerOwnsThePublicShortsUrl() throws Exception {
        final ListLinkHandlerFactory factory = YoutubeShortsLinkHandlerFactory.INSTANCE;

        assertEquals("https://www.youtube.com/shorts",
                factory.fromId(YoutubeShortsLinkHandlerFactory.KIOSK_ID).getUrl());
        assertTrue(factory.acceptUrl("https://www.youtube.com/shorts"));
        assertFalse(factory.acceptUrl("https://www.youtube.com/feed/trending"));
    }
}
