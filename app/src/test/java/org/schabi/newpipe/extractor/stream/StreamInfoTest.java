package org.schabi.newpipe.extractor.stream;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.StreamingService;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreamInfoTest {

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
