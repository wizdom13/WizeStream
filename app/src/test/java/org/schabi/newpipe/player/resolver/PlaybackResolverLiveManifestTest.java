package org.schabi.newpipe.player.resolver;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.source.MediaSource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public class PlaybackResolverLiveManifestTest {
    @Test
    public void manifestOnlyYoutubeLiveCanSelectDashWithHlsFallback() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId());

        assertTrue(PlaybackResolver.isManifestOnlyYoutubeLive(info));
    }

    @Test
    public void misclassifiedManifestOnlyYoutubeLiveStillUsesLiveSourceSelection() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId());
        info.setStreamType(StreamType.VIDEO_STREAM);

        assertTrue(PlaybackResolver.isManifestOnlyYoutubeLive(info));
    }

    @Test
    public void youtubeLiveWithDirectStreamsRetainsDashPreference() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId());
        info.setVideoStreams(Collections.singletonList(mock(VideoStream.class)));

        assertFalse(PlaybackResolver.isManifestOnlyYoutubeLive(info));
    }

    @Test
    public void youtubeLiveWithVideoOnlyStreamsRetainsDirectStreamSelection() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId());
        info.setVideoOnlyStreams(Collections.singletonList(mock(VideoStream.class)));

        assertFalse(PlaybackResolver.isManifestOnlyYoutubeLive(info));
    }

    @Test
    public void nonYoutubeManifestOnlyLiveRetainsDashPreference() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId() + 1);

        assertFalse(PlaybackResolver.isManifestOnlyYoutubeLive(info));
    }

    @Test
    public void youtubeWithoutHlsDoesNotUseManifestOnlyFallback() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId());
        info.setHlsUrl("");

        assertFalse(PlaybackResolver.isManifestOnlyYoutubeLive(info));
    }

    @Test
    public void stalledManifestOnlyLiveCanUseHlsWithoutPreparingDash() {
        final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId());
        final PlayerDataSource dataSource = mock(PlayerDataSource.class);
        when(dataSource.getLiveHlsMediaSourceFactory())
                .thenReturn(new HlsMediaSource.Factory(new DefaultHttpDataSource.Factory()));

        final MediaSource source = PlaybackResolver.maybeBuildLiveMediaSource(
                dataSource, info, true);

        assertNotNull(source);
        assertTrue(source instanceof HlsMediaSource);
        verify(dataSource, never()).getLiveYoutubeDashMediaSourceFactory();
    }

    @Test
    public void invidiousDashOnlyLiveUsesRestrictedInstanceFactory() {
        InvidiousBackend.configure(true, "https://example.com");
        try {
            final StreamInfo info = createLiveInfo(ServiceList.YouTube.getServiceId());
            info.setHlsUrl("");
            final PlayerDataSource dataSource = mock(PlayerDataSource.class);
            when(dataSource.getLiveDashMediaSourceFactory(true)).thenReturn(
                    new DashMediaSource.Factory(new DefaultHttpDataSource.Factory()));
            final MediaSource source = PlaybackResolver.maybeBuildLiveMediaSource(dataSource, info);
            assertTrue(source instanceof DashMediaSource);
            verify(dataSource).getLiveDashMediaSourceFactory(true);
            verify(dataSource, never()).getLiveYoutubeDashMediaSourceFactory();
            verify(dataSource, never()).getLiveHlsMediaSourceFactory(true);
        } finally {
            InvidiousBackend.configure(false, "");
        }
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
