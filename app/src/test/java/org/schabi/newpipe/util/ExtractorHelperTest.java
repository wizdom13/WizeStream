package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExtractorHelperTest {
    @Test
    public void playlistAvatarBackfillRequiresMissingYouTubeAvatarAndUploader() {
        final int youtubeServiceId = ServiceList.YouTube.getServiceId();

        assertTrue(ExtractorHelper.shouldBackfillYouTubePlaylistUploaderAvatar(
                youtubeServiceId, "https://www.youtube.com/channel/test", ""));
        assertFalse(ExtractorHelper.shouldBackfillYouTubePlaylistUploaderAvatar(
                youtubeServiceId, "", ""));
        assertFalse(ExtractorHelper.shouldBackfillYouTubePlaylistUploaderAvatar(
                youtubeServiceId,
                "https://www.youtube.com/channel/test",
                "https://example.com/avatar.jpg"));
        assertFalse(ExtractorHelper.shouldBackfillYouTubePlaylistUploaderAvatar(
                youtubeServiceId + 1, "https://example.com/channel/test", ""));
    }

    @Test
    public void youtubeReloadResponseIsRetried() {
        assertTrue(ExtractorHelper.isTransientYouTubeReloadError(
                ServiceList.YouTube.getServiceId(),
                new ContentNotAvailableException("The page needs to be reloaded.")));
    }

    @Test
    public void genuineAvailabilityErrorsAreNotRetried() {
        final int youtubeServiceId = ServiceList.YouTube.getServiceId();

        assertFalse(ExtractorHelper.isTransientYouTubeReloadError(
                youtubeServiceId,
                new ContentNotAvailableException("This video is private")));
        assertFalse(ExtractorHelper.isTransientYouTubeReloadError(
                youtubeServiceId + 1,
                new ContentNotAvailableException("The page needs to be reloaded.")));
        assertFalse(ExtractorHelper.isTransientYouTubeReloadError(youtubeServiceId, null));
    }

    @Test
    public void lastUsableImageUrlIsSelected() {
        final List<Image> images = Arrays.asList(
                image("https://example.com/first.jpg"),
                null,
                image("https://example.com/last.jpg"),
                image(""));

        assertEquals(
                "https://example.com/last.jpg",
                ExtractorHelper.findLastNonEmptyImageUrl(images));
    }

    @Test
    public void missingImageListHasNoFallbackUrl() {
        assertNull(ExtractorHelper.findLastNonEmptyImageUrl(null));
        assertNull(ExtractorHelper.findLastNonEmptyImageUrl(Collections.emptyList()));
    }

    @Test
    public void playlistOwnerAvatarIsReusedWithoutAnotherLookup() {
        final List<StreamInfoItem> items = Arrays.asList(
                stream("first", "channel", null),
                stream("second", "channel", null));

        assertNull(ExtractorHelper.enrichKnownPlaylistItemUploaderAvatars(
                items, "channel", "avatar"));
        assertEquals("avatar", items.get(0).getUploaderAvatarUrl());
        assertEquals("avatar", items.get(1).getUploaderAvatarUrl());
    }

    @Test
    public void knownItemAvatarIsReusedForMatchingPlaylistItems() {
        final List<StreamInfoItem> items = Arrays.asList(
                stream("first", "channel", "cached-avatar"),
                stream("second", "channel", null));

        assertNull(ExtractorHelper.enrichKnownPlaylistItemUploaderAvatars(
                items, null, null));
        assertEquals("cached-avatar", items.get(1).getUploaderAvatarUrl());
    }

    @Test
    public void singleMissingUploaderRequestsOnlyOneChannelLookup() {
        final List<StreamInfoItem> items = Arrays.asList(
                stream("first", "channel", null),
                stream("second", "channel", null));

        assertEquals("channel", ExtractorHelper.enrichKnownPlaylistItemUploaderAvatars(
                items, null, null));
    }

    @Test
    public void mixedMissingUploadersDoNotTriggerPerVideoLookups() {
        final List<StreamInfoItem> items = Arrays.asList(
                stream("first", "channel-one", null),
                stream("second", "channel-two", null));

        assertNull(ExtractorHelper.enrichKnownPlaylistItemUploaderAvatars(
                items, null, null));
    }

    @Test
    public void resolvedAvatarDoesNotOverwriteExistingAvatar() {
        final List<StreamInfoItem> items = Arrays.asList(
                stream("first", "channel", "existing-avatar"),
                stream("second", "channel", null),
                stream("third", "other-channel", null));

        ExtractorHelper.applyPlaylistItemUploaderAvatar(items, "channel", "resolved-avatar");

        assertEquals("existing-avatar", items.get(0).getUploaderAvatarUrl());
        assertEquals("resolved-avatar", items.get(1).getUploaderAvatarUrl());
        assertNull(items.get(2).getUploaderAvatarUrl());
    }

    private static Image image(final String url) {
        return new Image(
                url,
                Image.HEIGHT_UNKNOWN,
                Image.WIDTH_UNKNOWN,
                Image.ResolutionLevel.UNKNOWN);
    }

    private static StreamInfoItem stream(final String url,
                                         final String uploaderUrl,
                                         final String uploaderAvatarUrl) {
        final StreamInfoItem item = new StreamInfoItem(
                ServiceList.YouTube.getServiceId(), url, url, StreamType.VIDEO_STREAM);
        item.setUploaderUrl(uploaderUrl);
        item.setUploaderAvatarUrl(uploaderAvatarUrl);
        return item;
    }
}
