package org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public class YoutubeAdaptiveDashManifestCreatorTest {
    @Test
    public void manifestContainsAllCompatibleRepresentations() throws Exception {
        final String manifest = YoutubeAdaptiveDashManifestCreator.fromProgressiveVideoStreams(
                List.of(
                        stream(137, "1080p", "avc1.640028", 4_000_000),
                        stream(136, "720p", "avc1.4d401f", 2_000_000),
                        stream(135, "480p", "avc1.4d401e", 1_000_000)),
                60);

        assertTrue(manifest.contains("<AdaptationSet"));
        assertTrue(manifest.contains("id=\"137\""));
        assertTrue(manifest.contains("id=\"136\""));
        assertTrue(manifest.contains("id=\"135\""));
        assertEquals(3, occurrences(manifest, "<Representation"));
        assertEquals(3, occurrences(manifest, "<SegmentBase"));
        assertEquals(3, occurrences(manifest, "<BaseURL>"));
    }

    @Test(expected = CreationException.class)
    public void adaptiveManifestRequiresAtLeastTwoRepresentations() throws Exception {
        YoutubeAdaptiveDashManifestCreator.fromProgressiveVideoStreams(
                List.of(stream(136, "720p", "avc1.4d401f", 2_000_000)),
                60);
    }

    private static int occurrences(final String value, final String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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
