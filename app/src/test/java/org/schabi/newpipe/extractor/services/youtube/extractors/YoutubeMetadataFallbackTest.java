package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonObject;
import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YoutubeMetadataFallbackTest {
    @Test
    void emptyCommentAvatarSourcesReturnAnEmptyUrl() throws Exception {
        final JsonObject payload = JsonUtils.toJsonObject(
                "{\"avatar\":{\"image\":{\"sources\":[]}}}");
        final YoutubeCommentsEUVMInfoItemExtractor extractor =
                new YoutubeCommentsEUVMInfoItemExtractor(
                        new JsonObject(), null, payload, new JsonObject(), "", null);

        assertEquals("", extractor.getUploaderAvatarUrl());
    }

    @Test
    void commentAvatarUsesTheFirstValidSource() throws Exception {
        final JsonObject payload = JsonUtils.toJsonObject("{\"avatar\":{\"image\":{"
                + "\"sources\":[{\"url\":\"https://example.com/avatar.jpg\","
                + "\"width\":48,\"height\":48}]}}}");
        final YoutubeCommentsEUVMInfoItemExtractor extractor =
                new YoutubeCommentsEUVMInfoItemExtractor(
                        new JsonObject(), null, payload, new JsonObject(), "", null);

        assertEquals("https://example.com/avatar.jpg", extractor.getUploaderAvatarUrl());
    }

    @Test
    void commentAvatarFallsBackToAuthorThumbnailUrl() throws Exception {
        final JsonObject payload = JsonUtils.toJsonObject("{\"author\":{"
                + "\"avatarThumbnailUrl\":\"https://yt3.ggpht.com/fallback=s88-c-k\"}} ");
        final YoutubeCommentsEUVMInfoItemExtractor extractor =
                new YoutubeCommentsEUVMInfoItemExtractor(
                        new JsonObject(), null, payload, new JsonObject(), "", null);

        assertEquals("https://yt3.ggpht.com/fallback=s88-c-k",
                extractor.getUploaderAvatarUrl());
    }

    @Test
    void streamUploaderPrefersSelectedPlayerAuthor() throws Exception {
        final JsonObject playerResponse = JsonUtils.toJsonObject(
                "{\"videoDetails\":{\"author\":\"Canonical uploader\"}}");
        final JsonObject microformat = JsonUtils.toJsonObject(
                "{\"ownerChannelName\":\"Microformat uploader\"}");
        final JsonObject ownerRenderer = JsonUtils.toJsonObject(
                "{\"title\":{\"runs\":[{\"text\":\"Watch-page uploader\"}]}}");

        assertEquals("Canonical uploader", YoutubeStreamExtractor.extractUploaderName(
                playerResponse, microformat, ownerRenderer));
    }

    @Test
    void streamUploaderFallsBackToWebMicroformatForTvPlayerResponse() throws Exception {
        final JsonObject playerResponse = JsonUtils.toJsonObject(
                "{\"videoDetails\":{\"channelId\":\"channel\"}}");
        final JsonObject microformat = JsonUtils.toJsonObject(
                "{\"ownerChannelName\":\"Microformat uploader\"}");

        assertEquals("Microformat uploader", YoutubeStreamExtractor.extractUploaderName(
                playerResponse, microformat, new JsonObject()));
    }

    @Test
    void streamUploaderFallsBackToNextOwnerRenderer() throws Exception {
        final JsonObject playerResponse = JsonUtils.toJsonObject(
                "{\"videoDetails\":{\"channelId\":\"channel\"}}");
        final JsonObject ownerRenderer = JsonUtils.toJsonObject(
                "{\"title\":{\"runs\":[{\"text\":\"Watch-page uploader\"}]}}");

        assertEquals("Watch-page uploader", YoutubeStreamExtractor.extractUploaderName(
                playerResponse, null, ownerRenderer));
    }

    @Test
    void streamUploaderKeepsUnknownFallbackWhenEverySourceIsMissing() throws Exception {
        final JsonObject playerResponse = JsonUtils.toJsonObject(
                "{\"videoDetails\":{\"channelId\":\"channel\"}}");

        assertEquals("Unknown", YoutubeStreamExtractor.extractUploaderName(
                playerResponse, null, new JsonObject()));
    }
}
