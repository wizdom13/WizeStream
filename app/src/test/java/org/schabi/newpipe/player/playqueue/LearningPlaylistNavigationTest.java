package org.schabi.newpipe.player.playqueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import android.util.Log;

import org.junit.Test;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.learning.LearningPlaylistContext;
import org.schabi.newpipe.learning.LearningPlaylistNavigation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.disposables.Disposable;

public class LearningPlaylistNavigationTest {
    private static final LearningPlaylistContext COURSE =
            new LearningPlaylistContext("remote-playlist:0:course", "Course", "study");

    @Test
    public void showsPositionNextAndEndIncludingDuplicateVideos() {
        final PlaylistPlayQueue queue = queue(null);
        assertEquals(1, state(queue).getPosition());
        assertEquals(Integer.valueOf(3), state(queue).getTotal());
        assertEquals("second", state(queue).getNext().getUrl());
        queue.setIndex(2);
        assertEquals(3, state(queue).getPosition());
        assertTrue(state(queue).getAtEnd());
        assertNull(state(queue).getNext());
    }

    @Test
    public void realPagesExtendCourseAndOnlyTheFinalPageSignalsEnd() {
        final PlaylistPlayQueue queue = queue(new Page("next"));
        queue.setIndex(2);
        assertFalse(state(queue).getAtEnd());
        assertNull(state(queue).getTotal());
        final var observer = queue.getNextPageObserver();
        observer.onSubscribe(Disposable.empty());
        observer.onSuccess(new ListExtractor.InfoItemsPage<>(
                List.of(item("fourth")), null, Collections.emptyList()));
        assertEquals(COURSE, queue.getLearningPlaylistContext());
        assertEquals("fourth", state(queue).getNext().getUrl());
        assertEquals(Integer.valueOf(4), state(queue).getTotal());
        queue.setIndex(3);
        assertTrue(state(queue).getAtEnd());
    }

    @Test
    public void failedPaginationIsNotAnEndOfCourse() {
        final PlaylistPlayQueue queue = queue(new Page("next"));
        queue.setIndex(2);
        try (var ignored = mockStatic(Log.class)) {
            queue.getNextPageObserver().onError(new IllegalStateException("offline"));
        }
        assertTrue(state(queue).getLoadError());
        assertFalse(state(queue).getAtEnd());
        assertNull(state(queue).getTotal());
    }

    @Test
    public void autoplayRecommendationsAreNotCountedAsLessons() {
        final PlaylistPlayQueue queue = queue(null);
        queue.setIndex(2);
        final PlayQueueItem recommendation = new PlayQueueItem(item("recommendation"));
        recommendation.setAutoQueued(true);
        queue.append(List.of(recommendation));
        assertTrue(state(queue).getAtEnd());
        assertEquals(Integer.valueOf(3), state(queue).getTotal());
        assertNull(state(queue).getNext());
        queue.setIndex(3);
        assertNull(state(queue));
    }

    @Test
    public void manualQueueChangesAndOtherProfilesDoNotClaimCourseOrder() {
        assertNull(LearningPlaylistNavigation.from(queue(null), "other", 0, "first"));
        assertNull(LearningPlaylistNavigation.from(queue(null), "study", 0, "unrelated"));
        final PlaylistPlayQueue moved = queue(null);
        moved.move(1, 2);
        assertNull(state(moved));
        final PlaylistPlayQueue removed = queue(null);
        removed.remove(1);
        assertNull(state(removed));
        final PlaylistPlayQueue appended = queue(null);
        appended.append(List.of(new PlayQueueItem(item("manual"))));
        assertNull(state(appended));
        final PlaylistPlayQueue shuffled = queue(null);
        shuffled.shuffle();
        assertNull(state(shuffled));
        final PlaylistPlayQueue standalone = queue(null);
        standalone.setLearningPlaylistContext(null);
        assertFalse(queue(null).equalStreams(standalone));
    }

    @Test
    public void originAndSelectedLessonSurviveQueueSerialization() throws Exception {
        final PlaylistPlayQueue queue = queue(null);
        queue.setIndex(1);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(queue);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            final PlayQueue restored = (PlayQueue) input.readObject();
            assertNotNull(state(restored));
            assertEquals(COURSE, restored.getLearningPlaylistContext());
            assertEquals(2, state(restored).getPosition());
            assertEquals("first", state(restored).getNext().getUrl());
        }
    }

    private static PlaylistPlayQueue queue(final Page nextPage) {
        final PlaylistPlayQueue queue = new PlaylistPlayQueue(0, "course", nextPage,
                List.of(item("first"), item("second"), item("first")), 0);
        queue.setLearningPlaylistContext(COURSE);
        return queue;
    }

    private static StreamInfoItem item(final String url) {
        return new StreamInfoItem(0, url, url, StreamType.VIDEO_STREAM);
    }

    private static LearningPlaylistNavigation state(final PlayQueue queue) {
        return LearningPlaylistNavigation.from(queue, "study", 0, queue.getItem().getUrl());
    }
}
