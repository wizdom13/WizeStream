package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrClientProfile;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YoutubeStreamExtractorSessionPoTokenTest {
    @AfterEach
    void tearDown() {
        NewPipe.setYoutubeSessionPoTokenProvider(null);
    }

    @Test
    void sabrInfoUsesRequestVisitorWithoutCallingProviderAgain() throws Exception {
        final AtomicInteger providerCalls = new AtomicInteger();
        NewPipe.setYoutubeSessionPoTokenProvider((clientName, localization, contentCountry,
                                                  loggedIn) -> {
            final int call = providerCalls.incrementAndGet();
            return new YoutubeSessionPoToken("later-visitor-" + call, "later-token-" + call);
        });
        final JsonObject response = JsonUtils.toJsonObject("{\"streamingData\":{"
                + "\"serverAbrStreamingUrl\":\"https://example.com/sabr\","
                + "\"adaptiveFormats\":[]}}");

        final YoutubeSabrInfo info = YoutubeStreamExtractor.buildSabrInfoFromPlayerResponse(
                "video", YoutubeSabrClientProfile.MWEB, "cpn", response, "request-visitor");

        assertEquals("request-visitor", info.getVisitorData());
        assertEquals(0, providerCalls.get());
    }
}
