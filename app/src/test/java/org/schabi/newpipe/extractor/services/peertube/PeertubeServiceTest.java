package org.schabi.newpipe.extractor.services.peertube;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubeAccountTabExtractor;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubeChannelTabExtractor;

import java.util.Collections;

public class PeertubeServiceTest {
    private final PeertubeService service = new PeertubeService(3);

    @Test
    public void videosTabUsesChannelTabExtractor() throws Exception {
        assertTrue(extractor(ChannelTabs.VIDEOS) instanceof PeertubeChannelTabExtractor);
    }

    @Test
    public void playlistsTabUsesChannelTabExtractor() throws Exception {
        assertTrue(extractor(ChannelTabs.PLAYLISTS) instanceof PeertubeChannelTabExtractor);
    }

    @Test
    public void channelsTabUsesAccountTabExtractor() throws Exception {
        assertTrue(extractor(ChannelTabs.CHANNELS) instanceof PeertubeAccountTabExtractor);
    }

    private ChannelTabExtractor extractor(final String tab) throws Exception {
        final ListLinkHandler handler = service.getChannelTabLHFactory().fromQuery(
                "video-channels/example",
                Collections.singletonList(
                        new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab)),
                null,
                service.getBaseUrl());
        return service.getChannelTabExtractor(handler);
    }
}
