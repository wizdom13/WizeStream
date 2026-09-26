package org.schabi.newpipe.player.resolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public class AdaptiveVideoQualityTest {
    @Test
    public void picksOneCompatibleCodecFamilyAndKeepsResolutionOrder() throws Exception {
        final VideoStream avc1080 = stream(137, "1080p", "avc1.640028", 4_000_000);
        final VideoStream avc720 = stream(136, "720p", "avc1.4d401f", 2_000_000);
        final VideoStream avc480 = stream(135, "480p", "avc1.4d401e", 1_000_000);
        final VideoStream vp9 = stream(248, "1080p", "vp09.00.41.08", 3_000_000);

        final List<VideoStream> candidates = AdaptiveVideoQuality.youtubeCandidates(
                List.of(avc1080, avc720, avc480, vp9), null);

        assertEquals(List.of("1080p", "720p", "480p"),
                candidates.stream().map(VideoStream::getResolution).toList());
        assertTrue(candidates.stream().allMatch(
                stream -> stream.getCodec().startsWith("avc1")));
    }

    @Test
    public void resolutionCapRemovesRepresentationsAboveTheLimit() throws Exception {
        final List<VideoStream> candidates = AdaptiveVideoQuality.youtubeCandidates(
                List.of(
                        stream(137, "1080p", "avc1.640028", 4_000_000),
                        stream(136, "720p", "avc1.4d401f", 2_000_000),
                        stream(135, "480p", "avc1.4d401e", 1_000_000)),
                "720p");

        assertEquals(List.of("720p", "480p"),
                candidates.stream().map(VideoStream::getResolution).toList());
    }

    @Test
    public void duplicateRepresentationsDoNotCreateDuplicateAdaptiveTracks() throws Exception {
        final VideoStream first720 = stream(136, "720p", "avc1.4d401f", 2_000_000);
        final VideoStream duplicate720 = stream(136, "720p", "avc1.4d401f", 2_500_000);
        final VideoStream lower = stream(135, "480p", "avc1.4d401e", 1_000_000);

        final List<VideoStream> candidates = AdaptiveVideoQuality.youtubeCandidates(
                List.of(first720, duplicate720, lower), null);

        assertEquals(List.of("720p", "480p"),
                candidates.stream().map(VideoStream::getResolution).toList());
    }

    @Test
    public void codecFamilyUsesTheStableCodecPrefix() {
        assertEquals("avc1", AdaptiveVideoQuality.codecFamily("avc1.640028"));
        assertEquals("vp09", AdaptiveVideoQuality.codecFamily("VP09.00.41.08"));
        assertEquals("av01", AdaptiveVideoQuality.codecFamily("av01.0.08M.08"));
    }

    private static VideoStream stream(final int itagId,
                                      final String resolution,
                                      final String codec,
                                      final int bitrate) throws ParsingException {
        final ItagItem itag = ItagItem.getItag(itagId);
        itag.setCodec(codec);
        itag.setBitrate(bitrate);
        itag.setWidth(resolution.startsWith("1080") ? 1920
                : resolution.startsWith("720") ? 1280 : 854);
        itag.setHeight(resolution.startsWith("1080") ? 1080
                : resolution.startsWith("720") ? 720 : 480);
        itag.setInitStart(0);
        itag.setInitEnd(99);
        itag.setIndexStart(100);
        itag.setIndexEnd(199);
        itag.setApproxDurationMs(60_000);

        return new VideoStream.Builder()
                .setId(Integer.toString(itagId))
                .setContent("https://example.com/videoplayback?itag=" + itagId, true)
                .setMediaFormat(itag.getMediaFormat())
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .setResolution(resolution)
                .setIsVideoOnly(true)
                .setItagItem(itag)
                .build();
    }
}
