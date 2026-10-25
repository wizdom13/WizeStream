package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.preference.PreferenceManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 35})
public class VideoCodecPolicyTest {
    @Test
    public void disablingAv1PreservesAvcAndVp9AtSixtyFps() throws Exception {
        final Context context = RuntimeEnvironment.getApplication();
        final var av1 = stream(399, "av01.0.08M.08", 60);
        final var missingCodec = stream(698, null, 60);
        final var avc = stream(299, "avc1.64002a", 60);
        final var vp9 = stream(303, "vp09.00.41.08", 60);
        final var streams = List.of(av1, missingCodec, avc, vp9);
        assertEquals(streams, VideoCodecPolicy.allowedStreams(context, streams));
        setAllowed(context, false);
        assertEquals(List.of(avc, vp9), VideoCodecPolicy.allowedStreams(context, streams));
        assertEquals(60, avc.getFps());
        assertEquals(60, vp9.getFps());
        setAllowed(context, true);
        assertEquals(streams, VideoCodecPolicy.allowedStreams(context, streams));
    }

    @Test
    public void manifestAndDecoderFilteringAgreeAndPreserveDelegateArguments() throws Exception {
        final Context context = RuntimeEnvironment.getApplication();
        final var requests = new ArrayList<String>();
        final var selector = VideoCodecPolicy.decoderSelector(context, (mime, secure, tunnel) -> {
            requests.add(mime + ":" + secure + ":" + tunnel);
            return List.of();
        });
        setAllowed(context, false);
        assertFalse(VideoCodecPolicy.allowsFormat(context,
                new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_AV1).build()));
        assertFalse(VideoCodecPolicy.allowsFormat(context,
                new Format.Builder().setCodecs("AV01.0.08M.08, mp4a.40.2").build()));
        assertTrue(VideoCodecPolicy.allowsFormat(context,
                new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()));
        selector.getDecoderInfos(MimeTypes.VIDEO_AV1, false, false);
        assertTrue(requests.isEmpty());
        selector.getDecoderInfos(MimeTypes.VIDEO_H264, true, true);
        assertEquals(List.of(MimeTypes.VIDEO_H264 + ":true:true"), requests);
        setAllowed(context, true);
        selector.getDecoderInfos(MimeTypes.VIDEO_AV1, false, false);
        assertEquals(2, requests.size());
    }

    @Test
    public void av1LabelsUseActualFrameRateAndKeepHighBitrateVariant() throws Exception {
        assertEquals("1080p60 AV1", stream(399, "av01", 60).getResolution());
        assertEquals("1080p AV1", stream(399, "av01", 30).getResolution());
        assertEquals("1080p50 AV1 High", stream(699, "av01", 50).getResolution());
        assertEquals("1080p60", stream(299, "avc1", 60).getResolution());
    }

    private static void setAllowed(final Context context, final boolean allowed) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(context.getString(R.string.allow_av1_key), allowed).commit();
    }

    private static VideoStream stream(final int id, final String codec, final int fps)
            throws Exception {
        final var itag = ItagItem.getItag(id);
        itag.setCodec(codec);
        itag.setFps(fps);
        return new VideoStream.Builder().setId(Integer.toString(id))
                .setContent("https://example.com/video", true)
                .setMediaFormat(itag.getMediaFormat()).setIsVideoOnly(true)
                .setResolution(itag.getResolutionString()).setItagItem(itag).build();
    }
}
