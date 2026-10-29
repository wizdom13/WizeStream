package org.schabi.newpipe.player.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

/** Owns resolution-change feedback for an attached video player. */
final class PlayerQualityFeedback {
    private final Player player;
    private final AutoQualityFeedback feedback = new AutoQualityFeedback();
    private final PlayerQualityOverlay overlay = new PlayerQualityOverlay();

    PlayerQualityFeedback(final Player player) {
        this.player = player;
    }

    void onVideoSizeChanged(final VideoSize size, final View anchor) {
        final String resolution = size.height > 0 ? size.height + "p" : null;
        final String streamKey = player.getCurrentStreamInfo()
                .map(info -> info.getServiceId() + ":" + info.getUrl()).orElse(null);
        final AutoQualityFeedback.Change change = feedback.update(streamKey,
                player.isLiveQualityPlayback() && player.isAutoLiveQualitySelected(), resolution);
        if (change != null && anchor.isAttachedToWindow() && anchor.isShown()) {
            overlay.show(anchor,
                    anchor.getContext().getString(R.string.live_auto_quality_changed,
                            change.from(), change.to()));
        }
    }

    @Nullable
    String selectedAdaptiveResolution(@NonNull final Tracks currentTracks) {
        @Nullable final MediaItemTag metadata = player.getCurrentMetadata();
        if (metadata == null || metadata.getMaybeQuality().isEmpty()) {
            return null;
        }

        final List<VideoStream> streams =
                metadata.getMaybeQuality().get().getSortedVideoStreams();
        for (final Tracks.Group group : currentTracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO || !group.isSelected()) {
                continue;
            }
            for (int index = 0; index < group.length; index++) {
                if (!group.isTrackSelected(index)) {
                    continue;
                }
                final String formatId = group.getMediaTrackGroup().getFormat(index).id;
                if (formatId == null) {
                    continue;
                }
                for (final VideoStream stream : streams) {
                    if (formatId.equals(Integer.toString(stream.getItag()))) {
                        return stream.getResolution();
                    }
                }
            }
        }
        return null;
    }
    void reset() {
        feedback.reset();
        overlay.clear();
    }
}
