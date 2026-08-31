package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CommentTextSizeHelperTest {
    @Test
    public void acceptsEverySupportedCommentTextSize() {
        assertEquals(12, CommentTextSizeHelper.parseCommentTextSize("12"));
        assertEquals(14, CommentTextSizeHelper.parseCommentTextSize("14"));
        assertEquals(16, CommentTextSizeHelper.parseCommentTextSize("16"));
        assertEquals(18, CommentTextSizeHelper.parseCommentTextSize("18"));
    }

    @Test
    public void invalidOrMissingValuesFallBackToMedium() {
        assertEquals(14, CommentTextSizeHelper.parseCommentTextSize(null));
        assertEquals(14, CommentTextSizeHelper.parseCommentTextSize(""));
        assertEquals(14, CommentTextSizeHelper.parseCommentTextSize("15"));
        assertEquals(14, CommentTextSizeHelper.parseCommentTextSize("large"));
    }
}
