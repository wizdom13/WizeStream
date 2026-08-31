package org.schabi.newpipe.fragments.list.comments;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class CommentTextSizeIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java")
            : Path.of("app/src/main/java");

    @Test
    public void commentBodiesAndReplyHeadersUseTheSharedPreference() throws Exception {
        final String holder = read("org/schabi/newpipe/info_list/holder/"
                + "CommentInfoItemHolder.java");
        final String replies = read("org/schabi/newpipe/fragments/list/comments/"
                + "CommentRepliesFragment.java");
        final String baseList = read("org/schabi/newpipe/fragments/list/"
                + "BaseListFragment.java");

        assertTrue(holder.contains("applyCommentTextSize(itemContentView)"));
        assertTrue(replies.contains("applyCommentTextSize(binding.commentContent)"));
        assertTrue(replies.contains("applyCommentTextSize(headerBinding.commentContent)"));
        assertTrue(baseList.contains("R.string.comment_text_size_key"));
        assertTrue(baseList.contains("applyPendingCommentTextSizeUpdate()"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
    }
}
