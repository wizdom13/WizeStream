package org.schabi.newpipe.util;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentFilterRulesTest {
    @Test
    void defaultsLeaveCommentsVisible() {
        assertFalse(new CommentFilterRules("", "", 0).isBlocked(comment("hello", "A", 0)));
    }

    @Test
    void keywordsMatchTextAndAuthorsMatchWholeNames() {
        final var rules = new CommentFilterRules("Spoiler\nfree prize", "Spam Bot", 0);
        assertTrue(rules.isBlocked(comment("A SPOILER here", "A", 20)));
        assertTrue(rules.isBlocked(comment("hello", "spam bot", 20)));
        assertFalse(rules.isBlocked(comment("hello", "spam bot fan", 20)));
        assertFalse(rules.isBlocked(comment("hello", "Spoiler", 20)));
    }

    @Test
    void thresholdIncludesBoundaryAndPreservesUnknownCounts() {
        final var rules = new CommentFilterRules("", "", 10);
        assertTrue(rules.isBlocked(comment("hello", "A", 9)));
        assertFalse(rules.isBlocked(comment("hello", "A", 10)));
        assertFalse(rules.isBlocked(comment("hello", "A", -1)));
    }

    private static CommentsInfoItem comment(final String text, final String author,
                                            final int likes) {
        final var item = new CommentsInfoItem(0, "https://example.com/comment", "name");
        item.setCommentText(text);
        item.setUploaderName(author);
        item.setLikeCount(likes);
        return item;
    }
}
