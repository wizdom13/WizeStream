package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class DanmakuControllerTest {
    @Test
    public void seekIndexFindsFirstCommentAtOrAfterPosition() {
        final List<BulletCommentsInfoItem> comments = new ArrayList<>();
        comments.add(commentAt(1_000L));
        comments.add(commentAt(2_000L));
        comments.add(commentAt(5_000L));

        assertEquals(0, DanmakuController.firstCommentAtOrAfter(comments, 0L));
        assertEquals(1, DanmakuController.firstCommentAtOrAfter(comments, 1_001L));
        assertEquals(2, DanmakuController.firstCommentAtOrAfter(comments, 2_001L));
        assertEquals(3, DanmakuController.firstCommentAtOrAfter(comments, 6_000L));
    }

    @Test
    public void seekIndexIncludesCommentExactlyAtPosition() {
        final List<BulletCommentsInfoItem> comments = List.of(
                commentAt(1_000L),
                commentAt(2_000L));

        assertEquals(1, DanmakuController.firstCommentAtOrAfter(comments, 2_000L));
    }

    private static BulletCommentsInfoItem commentAt(final long millis) {
        final BulletCommentsInfoItem item = new BulletCommentsInfoItem(0, "url", "comment");
        item.setDuration(Duration.ofMillis(millis));
        item.setCommentText("test");
        return item;
    }
}
