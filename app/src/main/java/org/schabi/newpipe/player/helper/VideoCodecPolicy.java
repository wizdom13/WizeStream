package org.schabi.newpipe.player.helper;

import android.content.Context;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Applies the device's codec preference to extracted streams and manifest playback. */
public final class VideoCodecPolicy {
    private VideoCodecPolicy() { }

    public static boolean allowsAv1(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.allow_av1_key), true);
    }

    public static boolean isAv1(final String codec) {
        if (codec == null) {
            return false;
        }
        for (final String entry : codec.toLowerCase(Locale.ROOT).split(",")) {
            final String family = entry.trim().split("\\.", 2)[0];
            if ("av01".equals(family) || "av1".equals(family)) {
                return true;
            }
        }
        return false;
    }

    public static List<VideoStream> allowedStreams(final Context context,
                                                   final List<VideoStream> streams) {
        if (allowsAv1(context)) {
            return streams;
        }
        return streams.stream().filter(stream -> !isAv1(stream.getCodec())
                && (stream.getItagItem() == null || !stream.getItagItem().isAv1()))
                .collect(Collectors.toList());
    }

    public static boolean allowsFormat(final Context context, final Format format) {
        return allowsAv1(context) || !(MimeTypes.VIDEO_AV1.equals(format.sampleMimeType)
                || isAv1(format.codecs));
    }

    public static MediaCodecSelector decoderSelector(final Context context,
                                                     final MediaCodecSelector delegate) {
        // Returning no decoders also prevents automatic manifest selection of AV1 tracks.
        return (mimeType, secure, tunneling) ->
                MimeTypes.VIDEO_AV1.equals(mimeType) && !allowsAv1(context)
                        ? Collections.emptyList()
                        : delegate.getDecoderInfos(mimeType, secure, tunneling);
    }
}
