package org.schabi.newpipe.player.event;

import com.google.android.exoplayer2.PlaybackParameters;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.helper.SleepTimer;
import org.schabi.newpipe.player.mediaitem.MediaItemTag;
import org.schabi.newpipe.player.playqueue.PlayQueue;

public interface PlayerEventListener {
    void onQueueUpdate(PlayQueue queue);
    void onPlaybackUpdate(int state, int repeatMode, boolean shuffled,
                          PlaybackParameters parameters);
    void onProgressUpdate(int currentProgress, int duration, int bufferPercent);
    void onMetadataUpdate(StreamInfo info, PlayQueue queue);

    /**
     * Reports metadata for media that does not originate from an online extractor.
     *
     * @param tag metadata for the current media item
     * @param queue the active play queue
     */
    default void onMetadataUpdate(final MediaItemTag tag, final PlayQueue queue) { }
    default void onAudioTrackUpdate() { }
    default void onSleepTimerChanged(final SleepTimer.Mode mode,
                                     final long remainingMillis,
                                     final boolean fadeOutEnabled) { }
    void onServiceStopped();
}
