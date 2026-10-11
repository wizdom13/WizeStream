package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.learning.LearningPlaylistContext;
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue;

import java.lang.reflect.Field;
import java.util.List;

public class VideoDetailLearningQueueTest {
    @Test
    public void serviceReconnectCannotReplaceSelectedLessonWithFirstLesson() throws Exception {
        final VideoDetailFragment fragment = mock(VideoDetailFragment.class, CALLS_REAL_METHODS);
        final PlaylistPlayQueue selected = queue(1);
        fragment.playQueue = selected;
        final Field pending = VideoDetailFragment.class.getDeclaredField("pendingLearningQueue");
        pending.setAccessible(true);
        pending.set(fragment, selected);

        final PlaylistPlayQueue old = queue(0);
        assertTrue(fragment.isStaleLearningQueue(old));
        fragment.onQueueUpdate(old);
        assertSame(selected, fragment.playQueue);
        // A separately restored queue is accepted only at the selected lesson.
        assertFalse(fragment.isStaleLearningQueue(queue(1)));
        // A matching video without its course context is not a replacement.
        old.setIndex(1);
        old.setLearningPlaylistContext(null);
        assertTrue(fragment.isStaleLearningQueue(old));
    }

    @Test
    public void ordinaryPlaybackKeepsReceivingQueueChanges() {
        final VideoDetailFragment fragment = mock(VideoDetailFragment.class, CALLS_REAL_METHODS);
        assertFalse(fragment.isStaleLearningQueue(queue(0)));
    }

    private static PlaylistPlayQueue queue(final int index) {
        final PlaylistPlayQueue queue = new PlaylistPlayQueue(0, "course", null,
                List.of(new StreamInfoItem(0, "first", "First", StreamType.VIDEO_STREAM),
                        new StreamInfoItem(0, "second", "Second", StreamType.VIDEO_STREAM)), index);
        queue.setLearningPlaylistContext(new LearningPlaylistContext("course", "Course", "study"));
        return queue;
    }
}
