package org.schabi.newpipe.extractor.services.peertube;

import static org.junit.Assert.assertEquals;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.Test;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubeChannelInfoItemExtractor;
import org.schabi.newpipe.extractor.services.peertube.extractors.PeertubeStreamInfoItemExtractor;

public class PeertubeAvatarTest {
    private static final String BASE_URL = "https://instance.example";

    @Test
    public void usesLargestCurrentAvatarAndPrefersRemoteFileUrl() throws Exception {
        final JsonObject actor = JsonParser.object().from("""
                {"avatars":[
                  {"width":48,"fileUrl":"https://cdn.example/small.jpg"},
                  {"width":120,"path":"/old.jpg","fileUrl":"https://cdn.example/large.jpg"}
                ],"avatar":{"path":"/legacy.jpg"}}
                """);
        assertEquals("https://cdn.example/large.jpg",
                PeertubeParsingHelper.getAvatarUrl(actor, BASE_URL));
        assertEquals("https://cdn.example/large.jpg",
                new PeertubeChannelInfoItemExtractor(actor, BASE_URL).getThumbnailUrl());
        final JsonObject video = new JsonObject();
        video.put("account", actor);
        assertEquals("https://cdn.example/large.jpg",
                new PeertubeStreamInfoItemExtractor(video, BASE_URL).getUploaderAvatarUrl());
    }

    @Test
    public void supportsPathOnlyArraysAndLegacyServers() throws Exception {
        for (final String payload : new String[]{
                "{\"avatars\":[{\"path\":\"/avatar.jpg\"}]}",
                "{\"avatar\":{\"path\":\"/avatar.jpg\"}}",
                "{\"avatars\":[],\"avatar\":{\"path\":\"/avatar.jpg\"}}"}) {
            assertEquals(BASE_URL + "/avatar.jpg", PeertubeParsingHelper.getAvatarUrl(
                    JsonParser.object().from(payload), BASE_URL));
        }
    }

    @Test
    public void resolvesAbsoluteAndProtocolRelativeUrlsWithoutPrefixingInstance() throws Exception {
        for (final String url : new String[]{
                "https://cdn.example/avatar.jpg", "//cdn.example/avatar.jpg"}) {
            final JsonObject actor = JsonParser.object().from(
                    "{\"avatar\":{\"path\":\"" + url + "\"}}");
            assertEquals("https://cdn.example/avatar.jpg",
                    PeertubeParsingHelper.getAvatarUrl(actor, BASE_URL));
        }
    }

    @Test
    public void skipsMissingAndMalformedOptionalImages() throws Exception {
        final JsonObject actor = JsonParser.object().from("""
                {"avatars":[null,{},
                  {"width":900,"fileUrl":"javascript:alert(1)"},
                  {"width":100,"fileUrl":"https://bad host/image","path":"/valid.jpg"}
                ]}
                """);
        assertEquals(BASE_URL + "/valid.jpg",
                PeertubeParsingHelper.getAvatarUrl(actor, BASE_URL));
        assertEquals("", PeertubeParsingHelper.getAvatarUrl(new JsonObject(), BASE_URL));
        assertEquals("", PeertubeParsingHelper.getAvatarUrl(null, BASE_URL));
        assertEquals("", new PeertubeChannelInfoItemExtractor(
                new JsonObject(), BASE_URL).getThumbnailUrl());
    }
}
