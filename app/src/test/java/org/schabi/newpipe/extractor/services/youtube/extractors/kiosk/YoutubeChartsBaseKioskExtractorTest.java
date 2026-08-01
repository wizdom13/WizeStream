package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeChartsBaseKioskExtractorTest {
    @Test
    void categoryKiosksUseDistinctChartTypes() throws Exception {
        final JsonObject musicRequest = requestFor(YoutubeTrendingMusicExtractor.CHART_TYPE);
        final JsonObject moviesRequest =
                requestFor(YoutubeTrendingMoviesAndShowsTrailersExtractor.CHART_TYPE);

        final String musicQuery = musicRequest.getString("query");
        final String moviesQuery = moviesRequest.getString("query");

        assertNotEquals(musicQuery, moviesQuery);
        assertTrue(musicQuery.endsWith("chart_params_chart_type=TRENDING_VIDEOS"));
        assertTrue(moviesQuery.endsWith("chart_params_chart_type=TRENDING_MOVIES"));
        assertEquals("AE", musicRequest.getObject("context").getObject("client")
                .getString("gl"));
    }

    private static JsonObject requestFor(final String chartType) throws Exception {
        final byte[] body = YoutubeChartsBaseKioskExtractor.buildRequestBody(
                Localization.DEFAULT, new ContentCountry("AE"), chartType);
        return JsonParser.object().from(new String(body, StandardCharsets.UTF_8));
    }
}
