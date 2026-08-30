package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.Test;

public class YoutubeStreamInfoItemExtractorTest {

    @Test
    public void uploaderNamesContainingViewAreNotParsedAsViewCounts() throws Exception {
        final JsonObject videoInfo = JsonParser.object().from(
                "{\"videoInfo\":{\"runs\":["
                        + "{\"text\":\"I-Genso - One Piece Analytiker ne-SerienReviewer\"},"
                        + "{\"text\":\"2 days ago\"}]}}"
        );

        assertEquals(-1L, new YoutubeStreamInfoItemExtractor(videoInfo, null).getViewCount());
    }

    @Test
    public void numericFallbackViewCountsAreStillParsed() throws Exception {
        final JsonObject videoInfo = JsonParser.object().from(
                "{\"videoInfo\":{\"runs\":[{\"text\":\"1.2K views\"}]}}"
        );

        assertEquals(1_200L, new YoutubeStreamInfoItemExtractor(videoInfo, null).getViewCount());
    }

    @Test
    public void digitDetectionDoesNotRequireJavaStreamApis() {
        assertTrue(YoutubeStreamInfoItemExtractor.containsDigit("1.2K views"));
        assertTrue(YoutubeStreamInfoItemExtractor.containsDigit("١٢٣ مشاهدة"));
        assertFalse(YoutubeStreamInfoItemExtractor.containsDigit("no views"));
    }

    @Test
    public void hiddenViewCountsReturnUnknown() throws Exception {
        assertEquals(
                -1L,
                new YoutubeStreamInfoItemExtractor(new JsonObject(), null).getViewCount());
    }
}
