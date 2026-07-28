/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package us.shandian.giga.get;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public class SabrDownloadStreamHelperTest {
    @Test
    public void sabrOnlyMediaRemainAvailableForDownloadDialog() {
        final AudioStream audio = audioStream("sabr-audio", DeliveryMethod.SABR);
        final VideoStream video = new VideoStream.Builder()
                .setId("sabr-video")
                .setContent("stream:sabr-video", false)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setDeliveryMethod(DeliveryMethod.SABR)
                .setResolution("1080p")
                .setIsVideoOnly(true)
                .build();

        assertSame(audio, SabrDownloadStreamHelper
                .getDownloadableMediaStreams(List.of(audio))
                .get(0));
        assertSame(video, SabrDownloadStreamHelper
                .getDownloadableMediaStreams(List.of(video))
                .get(0));
    }

    @Test
    public void downloadableMediaStreamsIncludeProgressiveAndSabr() {
        final AudioStream progressive = audioStream("progressive",
                DeliveryMethod.PROGRESSIVE_HTTP);
        final AudioStream sabr = audioStream("sabr", DeliveryMethod.SABR);
        final AudioStream hls = audioStream("hls", DeliveryMethod.HLS);
        final AudioStream torrent = audioStream("torrent", DeliveryMethod.TORRENT);

        final List<AudioStream> result = SabrDownloadStreamHelper
                .getDownloadableMediaStreams(List.of(progressive, sabr, hls, torrent));

        assertEquals(2, result.size());
        assertSame(progressive, result.get(0));
        assertSame(sabr, result.get(1));
    }

    @Test
    public void downloadableMediaStreamsHandleMissingInput() {
        assertTrue(SabrDownloadStreamHelper
                .getDownloadableMediaStreams(null)
                .isEmpty());
    }

    private static AudioStream audioStream(final String id,
                                           final DeliveryMethod deliveryMethod) {
        return new AudioStream.Builder()
                .setId(id)
                .setContent("stream:" + id, false)
                .setMediaFormat(MediaFormat.M4A)
                .setDeliveryMethod(deliveryMethod)
                .setAverageBitrate(128)
                .build();
    }
}
