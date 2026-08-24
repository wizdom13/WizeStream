package org.schabi.newpipe.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.extractor.stream.DeliveryMethod.DASH;
import static org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public class MuxedAudioFallbackPolicyTest {
    @Test
    public void selectsSmallestProgressiveMuxedMp4() {
        final List<VideoStream> streams = List.of(
                video("720", MediaFormat.MPEG_4, "720p", false, PROGRESSIVE_HTTP),
                video("360", MediaFormat.MPEG_4, "360p", false, PROGRESSIVE_HTTP),
                video("1080-video-only", MediaFormat.MPEG_4, "1080p", true,
                        PROGRESSIVE_HTTP),
                video("144-webm", MediaFormat.WEBM, "144p", false, PROGRESSIVE_HTTP),
                video("240-dash", MediaFormat.MPEG_4, "240p", false, DASH));

        assertEquals(1, MuxedAudioFallbackPolicy.findFallbackVideoIndex(streams));
    }

    @Test
    public void returnsNoFallbackWithoutCompatibleMuxedMp4() {
        assertEquals(-1, MuxedAudioFallbackPolicy.findFallbackVideoIndex(List.of(
                video("video-only", MediaFormat.MPEG_4, "360p", true, PROGRESSIVE_HTTP),
                video("webm", MediaFormat.WEBM, "144p", false, PROGRESSIVE_HTTP))));
    }

    @Test
    public void createsM4aViewOfMuxedSource() {
        final VideoStream source = video("18", MediaFormat.MPEG_4, "360p", false,
                PROGRESSIVE_HTTP);

        final AudioStream fallback = MuxedAudioFallbackPolicy.createFallbackAudioStream(source);

        assertEquals("muxed-audio:18", fallback.getId());
        assertEquals(source.getContent(), fallback.getContent());
        assertTrue(fallback.isUrl());
        assertEquals(MediaFormat.M4A, fallback.getFormat());
        assertEquals(PROGRESSIVE_HTTP, fallback.getDeliveryMethod());
    }

    private static VideoStream video(final String id,
                                     final MediaFormat format,
                                     final String resolution,
                                     final boolean videoOnly,
                                     final org.schabi.newpipe.extractor.stream.DeliveryMethod
                                             deliveryMethod) {
        return new VideoStream.Builder()
                .setId(id)
                .setContent("https://example.com/" + id, true)
                .setMediaFormat(format)
                .setDeliveryMethod(deliveryMethod)
                .setResolution(resolution)
                .setIsVideoOnly(videoOnly)
                .build();
    }
}
