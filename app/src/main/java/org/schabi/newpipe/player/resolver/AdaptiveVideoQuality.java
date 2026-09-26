package org.schabi.newpipe.player.resolver;

import android.content.Context;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.util.ListHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Selects compatible YouTube representations for Media3 adaptive playback. */
final class AdaptiveVideoQuality {
    private AdaptiveVideoQuality() {
    }

    @NonNull
    static List<VideoStream> youtubeCandidates(@NonNull final Context context,
                                               @NonNull final List<VideoStream> streams) {
        final Map<String, LinkedHashMap<String, VideoStream>> groups = new LinkedHashMap<>();
        for (final VideoStream stream : streams) {
            if (!isEligible(context, stream)) {
                continue;
            }
            final String codecFamily = codecFamily(stream.getCodec());
            if (codecFamily.isEmpty()) {
                continue;
            }
            final String familyKey = stream.getFormatId() + ":" + codecFamily;
            final String representationKey = stream.getResolution() + ":" + stream.getFps();
            groups.computeIfAbsent(familyKey, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(representationKey, stream);
        }

        List<VideoStream> fallback = List.of();
        for (final LinkedHashMap<String, VideoStream> group : groups.values()) {
            final List<VideoStream> candidates = new ArrayList<>(group.values());
            if (fallback.isEmpty()) {
                fallback = candidates;
            }
            if (candidates.size() >= 2) {
                return candidates;
            }
        }
        return fallback;
    }

    private static boolean isEligible(@NonNull final Context context,
                                      @NonNull final VideoStream stream) {
        return stream.isVideoOnly()
                && stream.isUrl()
                && stream.getItagItem() != null
                && stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && stream.getFormat() != null
                && stream.getBitrate() > 0
                && stream.getWidth() > 0
                && stream.getHeight() > 0
                && stream.getInitStart() >= 0
                && stream.getInitEnd() >= 0
                && stream.getIndexStart() >= 0
                && stream.getIndexEnd() >= 0
                && ListHelper.isVideoStreamWithinResolutionLimit(context, stream);
    }

    @NonNull
    static String codecFamily(final String codec) {
        if (codec == null || codec.isBlank()) {
            return "";
        }
        final String normalized = codec.trim().toLowerCase(Locale.ROOT);
        final int separator = normalized.indexOf('.');
        return separator < 0 ? normalized : normalized.substring(0, separator);
    }
}
