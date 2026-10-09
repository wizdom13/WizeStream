package org.schabi.newpipe.extractor.stream;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreamInfoTest {

    @Test
    void normalizesNullManifestUrls() {
        final StreamInfo streamInfo = new StreamInfo();

        streamInfo.setHlsUrl(null);
        streamInfo.setDashMpdUrl(null);

        assertEquals("", streamInfo.getHlsUrl());
        assertEquals("", streamInfo.getDashMpdUrl());
    }

    @Test
    void acceptsHlsOnlyLiveStream() throws Exception {
        final StreamExtractor extractor = createExtractor();
        final String hlsUrl = "https://example.com/live.m3u8";
        when(extractor.getHlsUrl()).thenReturn(hlsUrl);

        final StreamInfo streamInfo = StreamInfo.getInfo(extractor);

        assertEquals(hlsUrl, streamInfo.getHlsUrl());
    }

    @Test
    void acceptsDashOnlyLiveStream() throws Exception {
        final StreamExtractor extractor = createExtractor();
        final String dashUrl = "https://example.com/live.mpd";
        when(extractor.getDashMpdUrl()).thenReturn(dashUrl);

        final StreamInfo streamInfo = StreamInfo.getInfo(extractor);

        assertEquals(dashUrl, streamInfo.getDashMpdUrl());
    }

    @Test
    void rejectsStreamWithoutDirectStreamsOrManifests() throws Exception {
        final StreamExtractor extractor = createExtractor();

        assertThrows(StreamInfo.StreamExtractException.class,
                () -> StreamInfo.getInfo(extractor));
    }

    @Test
    void failedStreamReportRetainsAllManifestAndFormatCauses() throws Exception {
        final StreamExtractor extractor = createExtractor();
        final ParsingException dash = new ParsingException("DASH proxy refused");
        final ExtractionException audio = new ExtractionException("Audio format malformed");
        final ExtractionException video = new ExtractionException("Media URL is not proxied");
        when(extractor.getDashMpdUrl()).thenThrow(dash);
        when(extractor.getAudioStreams()).thenThrow(audio);
        when(extractor.getVideoStreams()).thenThrow(video);

        final var failure = assertThrows(StreamInfo.StreamExtractException.class,
                () -> StreamInfo.getInfo(extractor));

        assertEquals(3, failure.getSuppressed().length);
        assertSame(dash, failure.getSuppressed()[0].getCause());
        assertSame(audio, failure.getSuppressed()[1].getCause());
        assertSame(video, failure.getSuppressed()[2].getCause());
        final var report = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(report));
        org.junit.jupiter.api.Assertions.assertTrue(report.toString()
                .contains("Media URL is not proxied"));
    }

    private static StreamExtractor createExtractor() throws Exception {
        final StreamExtractor extractor = mock(StreamExtractor.class);
        final StreamingService service = mock(StreamingService.class);

        when(extractor.getService()).thenReturn(service);
        when(extractor.getServiceId()).thenReturn(0);
        when(extractor.getUrl()).thenReturn("https://example.com/watch?v=live");
        when(extractor.getOriginalUrl()).thenReturn("https://example.com/watch?v=live");
        when(extractor.getStreamType()).thenReturn(StreamType.LIVE_STREAM);
        when(extractor.getId()).thenReturn("live");
        when(extractor.getName()).thenReturn("Live stream");
        when(extractor.getAgeLimit()).thenReturn(0);
        when(extractor.getAudioStreams()).thenReturn(Collections.emptyList());
        when(extractor.getVideoStreams()).thenReturn(Collections.emptyList());
        when(extractor.getVideoOnlyStreams()).thenReturn(Collections.emptyList());

        return extractor;
    }
}
