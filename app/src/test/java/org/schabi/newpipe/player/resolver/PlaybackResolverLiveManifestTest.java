package org.schabi.newpipe.player.resolver;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.Collections;

public class PlaybackResolverLiveManifestTest {
    @Test
    public void manifestOnlyYoutubeLivePrefersRefreshableHls() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId());

        assertTrue(PlaybackResolver.shouldPreferHlsForManifestOnlyYoutubeLive(info));
    }

    @Test
    public void youtubeLiveWithDirectStreamsRetainsDashPreference() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId());
        info.setVideoStreams(Collections.singletonList(mock(VideoStream.class)));

        assertFalse(PlaybackResolver.shouldPreferHlsForManifestOnlyYoutubeLive(info));
    }

    @Test
    public void nonYoutubeManifestOnlyLiveRetainsDashPreference() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId() + 1);

        assertFalse(PlaybackResolver.shouldPreferHlsForManifestOnlyYoutubeLive(info));
    }

    private static StreamInfo createLiveInfo(final int serviceId) {
        final StreamInfo info = new StreamInfo(serviceId, "live", "https://example.com/live",
                "Live stream");
        info.setStreamType(StreamType.LIVE_STREAM);
        info.setDashMpdUrl("https://example.com/live.mpd");
        info.setHlsUrl("https://example.com/live.m3u8");
        return info;
    }
}
