package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ServiceList;

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

    private static Image image(final String url) {
        return new Image(
                url,
                Image.HEIGHT_UNKNOWN,
                Image.WIDTH_UNKNOWN,
                Image.ResolutionLevel.UNKNOWN);
    }
}
