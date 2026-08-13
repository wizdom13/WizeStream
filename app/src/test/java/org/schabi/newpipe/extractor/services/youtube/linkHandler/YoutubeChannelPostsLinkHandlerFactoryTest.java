package org.schabi.newpipe.extractor.services.youtube.linkHandler;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YoutubeChannelPostsLinkHandlerFactoryTest {
    @Test
    void createsAndParsesPostsUrls() throws Exception {
        final YoutubeChannelTabLinkHandlerFactory factory =
                YoutubeChannelTabLinkHandlerFactory.getInstance();
        final ListLinkHandler posts = factory.fromUrl(
                "https://www.youtube.com/channel/UC123/posts");

        assertEquals(ChannelTabs.POSTS, posts.getContentFilters().get(0).getName());
        assertEquals("https://www.youtube.com/channel/UC123/posts", posts.getUrl());
    }

    @Test
    void normalizesLegacyCommunityUrlsToPosts() throws Exception {
        final ListLinkHandler posts = YoutubeChannelTabLinkHandlerFactory.getInstance().fromUrl(
                "https://www.youtube.com/channel/UC123/community");

        assertEquals(ChannelTabs.POSTS, posts.getContentFilters().get(0).getName());
        assertEquals("https://www.youtube.com/channel/UC123/posts", posts.getUrl());
    }
}
