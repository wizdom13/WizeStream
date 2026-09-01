package org.schabi.newpipe.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.post.PostInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Set;

class ContentBlockingHelperTest {
    @Test
    void blocksExactVideoUrl() {
        final ContentBlockingHelper.Rules rules = ContentBlockingHelper.Rules.create(
                true, Set.of("https://example.com/watch/1\tVideo"), Set.of(), "");

        assertTrue(rules.isBlocked(stream("https://example.com/watch/1", "Allowed title",
                "Channel", "https://example.com/channel")));
        assertFalse(rules.isBlocked(stream("https://example.com/watch/2", "Allowed title",
                "Channel", "https://example.com/channel")));
    }

    @Test
    void blocksChannelByUrlOrStoredDisplayName() {
        final ContentBlockingHelper.Rules rules = ContentBlockingHelper.Rules.create(
                true, Set.of(), Set.of("https://example.com/channel\tBlocked Channel"), "");

        assertTrue(rules.isBlocked(stream("video-1", "Title", "Different name",
                "https://example.com/channel")));
        assertTrue(rules.isBlocked(stream("video-2", "Title", "blocked channel", null)));
    }

    @Test
    void keywordMatchingIsCaseInsensitiveAndSupportsCommaOrLineBreaks() {
        final ContentBlockingHelper.Rules rules = ContentBlockingHelper.Rules.create(
                true, Set.of(), Set.of(), "Spoiler, clickbait\nRumor");

        assertTrue(rules.isBlocked(stream("video-1", "Major SPOILER", "Channel", null)));
        assertTrue(rules.isBlocked(stream("video-2", "Rumor report", "Network", null)));
        assertFalse(rules.isBlocked(stream("video-3", "Documentary", "Science", null)));
    }

    @Test
    void surroundingWhitespaceDoesNotTurnUploaderNamesIntoKeywordMatches() {
        final ContentBlockingHelper.Rules rules = ContentBlockingHelper.Rules.create(
                true, Set.of(), Set.of(), " ki ");

        assertTrue(rules.isBlocked(stream("video-1", "A KI lesson", "Science", null)));
        assertFalse(rules.isBlocked(stream("video-2", "Allowed title", "Ki Channel", null)));
    }

    @Test
    void uploaderNamesDoNotTriggerPlaylistOrPostKeywordRules() {
        final ContentBlockingHelper.Rules rules = ContentBlockingHelper.Rules.create(
                true, Set.of(), Set.of(), "ki");
        final PlaylistInfoItem playlist = new PlaylistInfoItem(0, "playlist-1", "Allowed list");
        playlist.setUploaderName("Ki Channel");
        final PostInfoItem post = new PostInfoItem(0, "post-1", "Allowed post");
        post.setUploaderName("Ki Channel");
        post.setContent("Allowed content");

        assertFalse(rules.isBlocked(playlist));
        assertFalse(rules.isBlocked(post));
    }

    @Test
    void sanitizesWhitespaceBlankEntriesAndCaseInsensitiveDuplicates() {
        assertEquals("Ki\nnews", ContentBlockingHelper.sanitizeKeywords(
                "  Ki  ,\n , ki\nnews\u00a0"));
    }

    @Test
    void disabledRulesDoNotHideAnything() {
        final ContentBlockingHelper.Rules rules = ContentBlockingHelper.Rules.create(
                false, Set.of("video-1\tVideo"), Set.of(), "video");

        assertFalse(rules.isBlocked(stream("video-1", "Video", "Channel", null)));
    }

    private static StreamInfoItem stream(final String url,
                                         final String title,
                                         final String uploader,
                                         final String uploaderUrl) {
        final StreamInfoItem item = new StreamInfoItem(0, url, title, StreamType.VIDEO_STREAM);
        item.setUploaderName(uploader);
        item.setUploaderUrl(uploaderUrl);
        return item;
    }
}
