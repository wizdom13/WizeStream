package org.schabi.newpipe.extractor.services.youtube.extractors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import com.grack.nanojson.JsonParser;

import org.junit.Test;
import org.schabi.newpipe.extractor.Extractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.lang.reflect.Field;

public class YoutubeLiveManifestTest {
    @Test
    public void misclassifiedLiveCanUseTheWebDashManifest() throws Exception {
        final YoutubeStreamExtractor extractor = fixture(true);
        assertEquals("https://example.com/live.mpd?existing=1&mpd_version=7",
                extractor.getDashMpdUrl());
    }

    @Test
    public void ordinaryVideoDoesNotEnterTheLiveDashPath() throws Exception {
        assertEquals("", fixture(false).getDashMpdUrl());
    }

    private static YoutubeStreamExtractor fixture(final boolean live) throws Exception {
        final YoutubeStreamExtractor extractor = mock(YoutubeStreamExtractor.class,
                CALLS_REAL_METHODS);
        final Field fetched = Extractor.class.getDeclaredField("pageFetched");
        fetched.setAccessible(true);
        fetched.setBoolean(extractor, true);
        set(extractor, "streamType", StreamType.VIDEO_STREAM);
        set(extractor, "playerResponse", JsonParser.object().from(
                "{\"videoDetails\":{\"isLive\":" + live + "}}"));
        set(extractor, "webStreamingData", JsonParser.object().from(
                "{\"dashManifestUrl\":\"https://example.com/live.mpd?existing=1\"}"));
        return extractor;
    }

    private static void set(final YoutubeStreamExtractor extractor, final String name,
                            final Object value) throws Exception {
        final Field field = YoutubeStreamExtractor.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(extractor, value);
    }
}
