package org.schabi.newpipe.extractor.services.youtube;

import static org.junit.Assert.assertEquals;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.Test;

public class YoutubeChannelAvatarTest {
    @Test
    public void extractsAvatarFromClassicChannelHeader() throws Exception {
        final JsonObject response = JsonParser.object().from("{\"header\":{"
                + "\"c4TabbedHeaderRenderer\":{\"avatar\":{\"thumbnails\":[{"
                + "\"url\":\"https://example.com/classic.jpg\","
                + "\"width\":88,\"height\":88}]}}}}");

        assertEquals("https://example.com/classic.jpg",
                YoutubeChannelHelper.getChannelAvatars(
                        YoutubeChannelHelper.getChannelHeader(response)).get(0).getUrl());
    }

    @Test
    public void extractsAvatarFromPageChannelHeader() throws Exception {
        final JsonObject response = JsonParser.object().from("{\"header\":{"
                + "\"pageHeaderRenderer\":{\"content\":{\"pageHeaderViewModel\":{"
                + "\"image\":{\"decoratedAvatarViewModel\":{\"avatar\":{"
                + "\"avatarViewModel\":{\"image\":{\"sources\":[{"
                + "\"url\":\"https://example.com/page.jpg\","
                + "\"width\":176,\"height\":176}]}}}}}}}}}}");

        assertEquals("https://example.com/page.jpg",
                YoutubeChannelHelper.getChannelAvatars(
                        YoutubeChannelHelper.getChannelHeader(response)).get(0).getUrl());
    }
}
