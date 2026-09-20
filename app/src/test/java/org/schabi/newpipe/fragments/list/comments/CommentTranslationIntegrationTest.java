package org.schabi.newpipe.fragments.list.comments;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class CommentTranslationIntegrationTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java")
            : Path.of("app/src/main/java");
    private final Path resourcesDirectory = Files.exists(Path.of("src/main/res"))
            ? Path.of("src/main/res")
            : Path.of("app/src/main/res");

    @Test
    public void commentsAndReplyHeadersExposeOnDeviceTranslation() throws Exception {
        final String holder = readSource(
                "org/schabi/newpipe/info_list/holder/CommentInfoItemHolder.java");
        final String replies = readSource(
                "org/schabi/newpipe/fragments/list/comments/CommentRepliesFragment.java");
        final String provider = readSource(
                "org/schabi/newpipe/util/CommentTranslationProvider.java");
        final String commentLayout = readResource("layout/list_comment_item.xml");
        final String replyHeader = readResource("layout/comment_replies_header.xml");

        assertTrue(holder.contains("CommentTranslationProvider.translate"));
        assertTrue(holder.contains("R.id.translate_button"));
        assertTrue(replies.contains("CommentTranslationProvider.translate"));
        assertTrue(replies.contains("binding.translateButton"));
        assertTrue(commentLayout.contains("@+id/translate_button"));
        assertTrue(replyHeader.contains("@+id/translate_button"));
        assertTrue(provider.contains("TranslationCapability.STATE_ON_DEVICE"));
        assertFalse(provider.contains("TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD"));
        assertFalse(provider.contains("http://"));
        assertFalse(provider.contains("https://"));
    }

    private String readSource(final String relativePath) throws Exception {
        return Files.readString(sourceDirectory.resolve(relativePath));
    }

    private String readResource(final String relativePath) throws Exception {
        return Files.readString(resourcesDirectory.resolve(relativePath));
    }
}
