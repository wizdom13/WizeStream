package org.schabi.newpipe.download;

import static org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

final class MuxedAudioFallbackPolicy {
    private MuxedAudioFallbackPolicy() {
    }

    static int findFallbackVideoIndex(@NonNull final List<VideoStream> streams) {
        int selectedIndex = -1;
        int selectedHeight = Integer.MAX_VALUE;
        for (int i = 0; i < streams.size(); i++) {
            final VideoStream stream = streams.get(i);
            if (stream.isVideoOnly()
                    || stream.getFormat() != MediaFormat.MPEG_4
                    || stream.getDeliveryMethod() != PROGRESSIVE_HTTP) {
                continue;
            }

            final int height = getHeight(stream);
            if (selectedIndex < 0 || height < selectedHeight) {
                selectedIndex = i;
                selectedHeight = height;
            }
        }
        return selectedIndex;
    }

    @NonNull
    static AudioStream createFallbackAudioStream(@NonNull final VideoStream source) {
        return new AudioStream.Builder()
                .setId("muxed-audio:" + source.getId())
                .setContent(source.getContent(), source.isUrl())
                .setMediaFormat(MediaFormat.M4A)
                .setDeliveryMethod(PROGRESSIVE_HTTP)
                .setAverageBitrate(AudioStream.UNKNOWN_BITRATE)
                .build();
    }

    private static int getHeight(@NonNull final VideoStream stream) {
        if (stream.getHeight() > 0) {
            return stream.getHeight();
        }
        final String resolution = stream.getResolution();
        if (resolution != null) {
            final int progressiveMarker = resolution.indexOf('p');
            if (progressiveMarker > 0) {
                try {
                    return Integer.parseInt(resolution.substring(0, progressiveMarker));
                } catch (final NumberFormatException ignored) {
                    // Treat malformed resolutions as unknown and prefer any known smaller stream.
                }
            }
        }
        return Integer.MAX_VALUE;
    }
}
